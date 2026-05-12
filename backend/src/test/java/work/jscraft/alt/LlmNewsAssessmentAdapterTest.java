package work.jscraft.alt;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.jscraft.alt.integrations.llm.LlmNewsAssessmentAdapter;
import work.jscraft.alt.integrations.openclaw.LlmSubprocessAdapter;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.news.application.NewsAssessmentEngine.AssessmentRequest;
import work.jscraft.alt.news.application.NewsAssessmentEngine.UsefulnessStatus;
import work.jscraft.alt.news.application.NewsAssessmentEngine.Verdict;
import work.jscraft.alt.trading.application.decision.LlmExecutableProperties;

import static org.assertj.core.api.Assertions.assertThat;

class LlmNewsAssessmentAdapterTest {

    private static final String FAKE_SCRIPT_RESOURCE = "/scripts/fake-openclaw.sh";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LlmExecutableProperties properties;
    private LlmSubprocessAdapter subprocessAdapter;
    private FakeRepository repository;
    private LlmNewsAssessmentAdapter adapter;
    private Path baseFakeScript;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new LlmExecutableProperties();
        properties.getOpenclaw().setTimeoutSeconds(5);
        properties.getNanobot().setTimeoutSeconds(5);
        subprocessAdapter = new LlmSubprocessAdapter(properties, objectMapper);
        repository = new FakeRepository();
        adapter = new LlmNewsAssessmentAdapter(repository, subprocessAdapter, properties, objectMapper);
        baseFakeScript = locateFakeScript();
    }

    // 1. No profile → UNCLASSIFIED with null id, no subprocess call.
    @Test
    void noProfileReturnsUnclassifiedWithNullId() throws IOException {
        repository.profiles = Collections.emptyList();
        // Point openclaw command at a path that would fail if invoked, to make
        // accidental subprocess invocation noticeable.
        properties.getOpenclaw().setCommand("/nonexistent/should/not/be/called");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
        assertThat(verdict.modelProfileId()).isNull();
    }

    // 2. USEFUL boolean
    @Test
    void usefulBooleanReturnsUseful() throws IOException {
        UUID id = installOpenclawProfile("news_useful_bool");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.USEFUL);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 3. NOT_USEFUL boolean
    @Test
    void notUsefulBooleanReturnsNotUseful() throws IOException {
        UUID id = installOpenclawProfile("news_not_useful_bool");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.NOT_USEFUL);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 4. USEFUL string
    @Test
    void usefulStringReturnsUseful() throws IOException {
        UUID id = installOpenclawProfile("news_useful_string");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.USEFUL);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 5. NOT_USEFUL string with case variation
    @Test
    void notUsefulStringCaseInsensitive() throws IOException {
        UUID id = installOpenclawProfile("news_not_useful_string_caps");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.NOT_USEFUL);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 6. Preamble before JSON
    @Test
    void preambleBeforeJsonStillParses() throws IOException {
        UUID id = installOpenclawProfile("news_preamble");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.USEFUL);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 7. Invalid JSON
    @Test
    void invalidJsonReturnsUnclassified() throws IOException {
        UUID id = installOpenclawProfile("news_not_json");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 8. Missing useful field
    @Test
    void missingUsefulFieldReturnsUnclassified() throws IOException {
        UUID id = installOpenclawProfile("news_missing_field");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 9. Numeric useful
    @Test
    void numericUsefulReturnsUnclassified() throws IOException {
        UUID id = installOpenclawProfile("news_numeric");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 10. Timeout
    @Test
    void timeoutReturnsUnclassified() throws IOException {
        UUID id = installOpenclawProfile("timeout");
        // Short timeout so the test stays fast.
        properties.getOpenclaw().setTimeoutSeconds(1);

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 11. Auth error
    @Test
    void authErrorReturnsUnclassified() throws IOException {
        UUID id = installOpenclawProfile("auth_error");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 12. Non-zero exit
    @Test
    void nonZeroExitReturnsUnclassified() throws IOException {
        UUID id = installOpenclawProfile("non_zero_exit");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 13. Empty stdout
    @Test
    void emptyStdoutReturnsUnclassified() throws IOException {
        UUID id = installOpenclawProfile("empty");

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 14. Multiple profiles → uses first.
    @Test
    void multipleProfilesUsesFirst() throws IOException {
        UUID firstId = installOpenclawProfile("news_useful_bool");
        UUID secondId = UUID.randomUUID();
        LlmModelProfileEntity second = newProfile(secondId, "openclaw", "model-b");
        repository.profiles = List.of(repository.profiles.get(0), second);

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.USEFUL);
        assertThat(verdict.modelProfileId()).isEqualTo(firstId);
        assertThat(verdict.modelProfileId()).isNotEqualTo(secondId);
    }

    // 15. Nanobot provider happy path
    @Test
    void nanobotProviderUseful() throws IOException {
        UUID id = UUID.randomUUID();
        LlmModelProfileEntity profile = newProfile(id, "nanobot", "nanobot-model");
        repository.profiles = List.of(profile);
        Path wrapper = writeWrapper("news_useful_nanobot", null);
        properties.getNanobot().setCommand(wrapper.toString());

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.USEFUL);
        assertThat(verdict.modelProfileId()).isEqualTo(id);
    }

    // 16. Null body → prompt contains placeholder. Verified via argv dump.
    @Test
    void nullBodySubstitutesPlaceholderInPrompt() throws IOException {
        UUID id = UUID.randomUUID();
        LlmModelProfileEntity profile = newProfile(id, "openclaw", "model-a");
        repository.profiles = List.of(profile);

        Path dumpFile = tempDir.resolve("argv-dump.txt");
        Path wrapper = writeWrapper("news_useful_bool", dumpFile);
        properties.getOpenclaw().setCommand(wrapper.toString());

        Verdict verdict = adapter.assess(new AssessmentRequest("title-X", "summary-Y", null));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.USEFUL);
        assertThat(verdict.modelProfileId()).isEqualTo(id);

        assertThat(Files.exists(dumpFile)).isTrue();
        List<String> args = Files.readAllLines(dumpFile);
        // The prompt is the value after "-m". Find that arg.
        int mIdx = args.indexOf("-m");
        assertThat(mIdx).isGreaterThanOrEqualTo(0);
        assertThat(mIdx + 1).isLessThan(args.size());
        // Each line in dump is exactly one argv element, but the prompt argv
        // itself contains newlines so it spans multiple lines in the dump.
        // Re-join all lines after the -m index into a single buffer to inspect.
        StringBuilder promptSink = new StringBuilder();
        for (int i = mIdx + 1; i < args.size(); i++) {
            promptSink.append(args.get(i)).append('\n');
        }
        String dumpedPrompt = promptSink.toString();
        assertThat(dumpedPrompt).contains("title-X");
        assertThat(dumpedPrompt).contains("summary-Y");
        assertThat(dumpedPrompt).contains("(없음)");
    }

    // ---------- Helpers ----------

    private UUID installOpenclawProfile(String fakeMode) throws IOException {
        UUID id = UUID.randomUUID();
        LlmModelProfileEntity profile = newProfile(id, "openclaw", "model-a");
        repository.profiles = List.of(profile);
        Path wrapper = writeWrapper(fakeMode, null);
        properties.getOpenclaw().setCommand(wrapper.toString());
        return id;
    }

    private LlmModelProfileEntity newProfile(UUID id, String provider, String modelName) {
        LlmModelProfileEntity entity = new LlmModelProfileEntity();
        entity.setPurpose("news_assessment");
        entity.setProvider(provider);
        entity.setModelName(modelName);
        entity.setEnabled(true);
        setId(entity, id);
        return entity;
    }

    private void setId(LlmModelProfileEntity entity, UUID id) {
        try {
            Field idField = entity.getClass().getSuperclass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to set entity id via reflection", ex);
        }
    }

    private Path locateFakeScript() {
        URL url = getClass().getResource(FAKE_SCRIPT_RESOURCE);
        if (url == null) {
            throw new IllegalStateException("fake script not found on classpath: " + FAKE_SCRIPT_RESOURCE);
        }
        return Path.of(url.getPath());
    }

    private Path writeWrapper(String fakeMode, Path dumpFile) throws IOException {
        Path wrapper = tempDir.resolve("wrapper-" + fakeMode + ".sh");
        StringBuilder body = new StringBuilder("#!/usr/bin/env bash\n");
        body.append("export FAKE_MODE=").append(fakeMode).append('\n');
        if (dumpFile != null) {
            body.append("export FAKE_DUMP_FILE=\"").append(dumpFile.toAbsolutePath()).append("\"\n");
        }
        body.append("exec \"").append(baseFakeScript.toAbsolutePath()).append("\" \"$@\"\n");
        Files.writeString(wrapper, body.toString());
        Files.setPosixFilePermissions(wrapper, PosixFilePermissions.fromString("rwxr-xr-x"));
        return wrapper;
    }

    private static class FakeRepository implements LlmModelProfileRepository {

        private List<LlmModelProfileEntity> profiles = new ArrayList<>();

        @Override
        public List<LlmModelProfileEntity> findByPurposeAndEnabledTrue(String purpose) {
            if (!"news_assessment".equals(purpose)) {
                return Collections.emptyList();
            }
            return profiles;
        }

        // ---- Unused JpaRepository methods ----

        @Override
        public List<LlmModelProfileEntity> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LlmModelProfileEntity> findAll(org.springframework.data.domain.Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LlmModelProfileEntity> findAllById(Iterable<UUID> uuids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity> List<S> saveAll(Iterable<S> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void flush() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity> S saveAndFlush(S entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllInBatch(Iterable<LlmModelProfileEntity> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<UUID> uuids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllInBatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmModelProfileEntity getOne(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmModelProfileEntity getById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmModelProfileEntity getReferenceById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity> List<S> findAll(org.springframework.data.domain.Example<S> example,
                org.springframework.data.domain.Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public org.springframework.data.domain.Page<LlmModelProfileEntity> findAll(
                org.springframework.data.domain.Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity> S save(S entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<LlmModelProfileEntity> findById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(LlmModelProfileEntity entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllById(Iterable<? extends UUID> uuids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll(Iterable<? extends LlmModelProfileEntity> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity, R> R findBy(
                org.springframework.data.domain.Example<S> example,
                java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity> java.util.Optional<S> findOne(
                org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity> org.springframework.data.domain.Page<S> findAll(
                org.springframework.data.domain.Example<S> example,
                org.springframework.data.domain.Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity> long count(org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends LlmModelProfileEntity> boolean exists(org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }
    }
}
