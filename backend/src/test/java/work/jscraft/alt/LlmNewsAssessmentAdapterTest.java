package work.jscraft.alt;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import work.jscraft.alt.integrations.llm.LlmHttpAdapter;
import work.jscraft.alt.integrations.llm.LlmNewsAssessmentAdapter;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.news.application.NewsAssessmentEngine.AssessmentRequest;
import work.jscraft.alt.news.application.NewsAssessmentEngine.UsefulnessStatus;
import work.jscraft.alt.news.application.NewsAssessmentEngine.Verdict;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmExecutableProperties;
import work.jscraft.alt.trading.application.decision.LlmRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmNewsAssessmentAdapterTest {

    private LlmModelProfileRepository repository;
    private LlmHttpAdapter httpAdapter;
    private LlmExecutableProperties properties;
    private LlmNewsAssessmentAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(LlmModelProfileRepository.class);
        httpAdapter = mock(LlmHttpAdapter.class);
        properties = new LlmExecutableProperties();
        properties.getOpenclaw().setTimeoutSeconds(30);
        properties.getNanobot().setTimeoutSeconds(60);
        adapter = new LlmNewsAssessmentAdapter(repository, httpAdapter, properties, new ObjectMapper());
    }

    @Test
    void noEnabledProfile_returnsUnclassified() {
        when(repository.findByPurposeAndEnabledTrue("news_assessment")).thenReturn(List.of());

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
        assertThat(verdict.modelProfileId()).isNull();
    }

    @Test
    void httpFailure_returnsUnclassifiedWithProfileId() throws Exception {
        LlmModelProfileEntity profile = profile("openclaw", "gpt-x");
        when(repository.findByPurposeAndEnabledTrue("news_assessment")).thenReturn(List.of(profile));
        when(httpAdapter.execute(any(LlmRequest.class))).thenReturn(LlmCallResult.timeout("boom"));

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
        assertThat(verdict.modelProfileId()).isEqualTo(profile.getId());
    }

    @Test
    void responseUsefulTrue_mapsToUseful() throws Exception {
        LlmModelProfileEntity profile = profile("openclaw", "gpt-x");
        when(repository.findByPurposeAndEnabledTrue("news_assessment")).thenReturn(List.of(profile));
        when(httpAdapter.execute(any(LlmRequest.class)))
                .thenReturn(LlmCallResult.success(200, "{\"useful\": true}", ""));

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.USEFUL);
    }

    @Test
    void responseUsefulFalse_withTextPrefix_mapsToNotUseful() throws Exception {
        LlmModelProfileEntity profile = profile("nanobot", "gpt-y");
        when(repository.findByPurposeAndEnabledTrue("news_assessment")).thenReturn(List.of(profile));
        when(httpAdapter.execute(any(LlmRequest.class)))
                .thenReturn(LlmCallResult.success(200, "preface text\n{\"useful\": false}", ""));

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.NOT_USEFUL);
    }

    @Test
    void invalidJson_returnsUnclassified() throws Exception {
        LlmModelProfileEntity profile = profile("openclaw", "gpt-x");
        when(repository.findByPurposeAndEnabledTrue("news_assessment")).thenReturn(List.of(profile));
        when(httpAdapter.execute(any(LlmRequest.class)))
                .thenReturn(LlmCallResult.success(200, "not json at all", ""));

        Verdict verdict = adapter.assess(new AssessmentRequest("t", "s", "b"));

        assertThat(verdict.status()).isEqualTo(UsefulnessStatus.UNCLASSIFIED);
    }

    private LlmModelProfileEntity profile(String provider, String modelName) throws Exception {
        LlmModelProfileEntity entity = new LlmModelProfileEntity();
        entity.setProvider(provider);
        entity.setModelName(modelName);
        entity.setPurpose("news_assessment");
        entity.setEnabled(true);

        Class<?> klass = entity.getClass();
        while (klass != null) {
            try {
                Field idField = klass.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(entity, UUID.randomUUID());
                return entity;
            } catch (NoSuchFieldException ignored) {
                klass = klass.getSuperclass();
            }
        }
        throw new IllegalStateException("no id field on " + entity.getClass());
    }
}
