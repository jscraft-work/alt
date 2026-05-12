package work.jscraft.alt;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.jscraft.alt.integrations.openclaw.LlmSubprocessAdapter;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmCallResult.CallStatus;
import work.jscraft.alt.trading.application.decision.LlmExecutableProperties;
import work.jscraft.alt.trading.application.decision.LlmRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmSubprocessAdapterTest {

    private static final String FAKE_SCRIPT_RESOURCE = "/scripts/fake-openclaw.sh";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LlmExecutableProperties properties;
    private LlmSubprocessAdapter adapter;
    private Path baseFakeScript;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        properties = new LlmExecutableProperties();
        adapter = new LlmSubprocessAdapter(properties, objectMapper);
        baseFakeScript = locateFakeScript();
    }

    // ---------- Pure function tests: buildArgv ----------

    @Test
    void buildArgvForOpenclawComposesExpectedSequence() {
        properties.getOpenclaw().setCommand("/bin/openclaw");
        UUID sid = UUID.randomUUID();
        LlmRequest req = new LlmRequest(sid, "openclaw", "gpt-5.5", "the prompt", Duration.ofSeconds(1));

        List<String> argv = adapter.buildArgv(req);

        assertThat(argv).containsExactly(
                "/bin/openclaw",
                "agent", "--local",
                "--session-id", sid.toString(),
                "-m", "the prompt",
                "--json");
    }

    @Test
    void buildArgvForNanobotComposesExpectedSequence() {
        properties.getNanobot().setCommand("/bin/nanobot");
        UUID sid = UUID.randomUUID();
        LlmRequest req = new LlmRequest(sid, "nanobot", "gpt-5.5", "the prompt", Duration.ofSeconds(1));

        List<String> argv = adapter.buildArgv(req);

        assertThat(argv).containsExactly(
                "/bin/nanobot",
                "agent", "--no-markdown",
                "-s", sid.toString(),
                "-m", "the prompt");
    }

    @Test
    void buildArgvRejectsUnknownEngine() {
        LlmRequest req = new LlmRequest(UUID.randomUUID(), "unknown", "x", "p", Duration.ofSeconds(1));

        assertThatThrownBy(() -> adapter.buildArgv(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    // ---------- Pure function tests: postProcessStdout ----------

    @Test
    void postProcessOpenclawUnwrapsPayloadsZeroText() {
        String unwrapped = adapter.postProcessStdout("openclaw", "{\"payloads\":[{\"text\":\"hi\"}]}");
        assertThat(unwrapped).isEqualTo("hi");
    }

    @Test
    void postProcessOpenclawFallsBackToRawWhenNotJson() {
        String result = adapter.postProcessStdout("openclaw", "not-json");
        assertThat(result).isEqualTo("not-json");
    }

    @Test
    void postProcessOpenclawFallsBackToRawWhenPayloadsEmpty() {
        String raw = "{\"payloads\":[]}";
        String result = adapter.postProcessStdout("openclaw", raw);
        assertThat(result).isEqualTo(raw);
    }

    @Test
    void postProcessNanobotStripsPrefixedFirstLine() {
        String stripped = adapter.postProcessStdout("nanobot", "🐈 nanobot\nhi");
        assertThat(stripped).isEqualTo("hi");
    }

    @Test
    void postProcessNanobotLeavesUnprefixedTextAlone() {
        String result = adapter.postProcessStdout("nanobot", "no prefix");
        assertThat(result).isEqualTo("no prefix");
    }

    // ---------- Subprocess integration tests using fake script ----------

    @Test
    void successCaseReturnsSuccessAndUnwrappedStdout() throws IOException {
        Path wrapper = writeWrapper("success_openclaw");
        properties.getOpenclaw().setCommand(wrapper.toString());

        LlmRequest request = new LlmRequest(
                UUID.randomUUID(),
                "openclaw",
                "gpt-5.5",
                "ignored",
                Duration.ofSeconds(5));

        LlmCallResult result = adapter.requestTradingDecision(request);

        assertThat(result.callStatus()).isEqualTo(CallStatus.SUCCESS);
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdoutText()).contains("cycleStatus");
        assertThat(result.stdoutText()).doesNotContain("payloads");
    }

    @Test
    void successNanobotStripsPrefixFromStdout() throws IOException {
        Path wrapper = writeWrapper("success_nanobot");
        properties.getNanobot().setCommand(wrapper.toString());

        LlmRequest request = new LlmRequest(
                UUID.randomUUID(),
                "nanobot",
                "gpt-5.5",
                "ignored",
                Duration.ofSeconds(5));

        LlmCallResult result = adapter.requestTradingDecision(request);

        assertThat(result.callStatus()).isEqualTo(CallStatus.SUCCESS);
        assertThat(result.stdoutText()).isEqualTo("hello-world");
    }

    @Test
    void timeoutKillsProcess() throws IOException {
        Path wrapper = writeWrapper("timeout");
        properties.getOpenclaw().setCommand(wrapper.toString());

        LlmRequest request = new LlmRequest(
                UUID.randomUUID(),
                "openclaw",
                "gpt-5.5",
                "ignored",
                Duration.ofMillis(300));

        LlmCallResult result = adapter.requestTradingDecision(request);

        assertThat(result.callStatus()).isEqualTo(CallStatus.TIMEOUT);
        assertThat(result.failureMessage()).contains("타임아웃");
    }

    @Test
    void stderrAuthKeywordTriggersAuthError() throws IOException {
        Path wrapper = writeWrapper("auth_error");
        properties.getOpenclaw().setCommand(wrapper.toString());

        LlmRequest request = new LlmRequest(
                UUID.randomUUID(),
                "openclaw",
                "gpt-5.5",
                "ignored",
                Duration.ofSeconds(5));

        LlmCallResult result = adapter.requestTradingDecision(request);

        assertThat(result.callStatus()).isEqualTo(CallStatus.AUTH_ERROR);
        assertThat(result.stderrText()).contains("Token refresh failed");
    }

    @Test
    void nonZeroExitIsClassified() throws IOException {
        Path wrapper = writeWrapper("non_zero_exit");
        properties.getOpenclaw().setCommand(wrapper.toString());

        LlmRequest request = new LlmRequest(
                UUID.randomUUID(),
                "openclaw",
                "gpt-5.5",
                "ignored",
                Duration.ofSeconds(5));

        LlmCallResult result = adapter.requestTradingDecision(request);

        assertThat(result.callStatus()).isEqualTo(CallStatus.NON_ZERO_EXIT);
        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test
    void emptyStdoutWithExitZeroIsEmptyOutput() throws IOException {
        Path wrapper = writeWrapper("empty");
        properties.getOpenclaw().setCommand(wrapper.toString());

        LlmRequest request = new LlmRequest(
                UUID.randomUUID(),
                "openclaw",
                "gpt-5.5",
                "ignored",
                Duration.ofSeconds(5));

        LlmCallResult result = adapter.requestTradingDecision(request);

        assertThat(result.callStatus()).isEqualTo(CallStatus.EMPTY_OUTPUT);
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    void stdoutErrorKeywordTriggersInvalidOutput() throws IOException {
        Path wrapper = writeWrapper("invalid_output");
        properties.getOpenclaw().setCommand(wrapper.toString());

        LlmRequest request = new LlmRequest(
                UUID.randomUUID(),
                "openclaw",
                "gpt-5.5",
                "ignored",
                Duration.ofSeconds(5));

        LlmCallResult result = adapter.requestTradingDecision(request);

        assertThat(result.callStatus()).isEqualTo(CallStatus.INVALID_OUTPUT);
        assertThat(result.stdoutText()).contains("Error calling");
    }

    // ---------- Helpers ----------

    private Path locateFakeScript() {
        URL url = getClass().getResource(FAKE_SCRIPT_RESOURCE);
        if (url == null) {
            throw new IllegalStateException("fake script not found on classpath: " + FAKE_SCRIPT_RESOURCE);
        }
        return Path.of(url.getPath());
    }

    private Path writeWrapper(String fakeMode) throws IOException {
        Path wrapper = tempDir.resolve("wrapper-" + fakeMode + ".sh");
        String body = "#!/usr/bin/env bash\nexport FAKE_MODE=" + fakeMode
                + "\nexec \"" + baseFakeScript.toAbsolutePath() + "\" \"$@\"\n";
        Files.writeString(wrapper, body);
        Files.setPosixFilePermissions(wrapper, PosixFilePermissions.fromString("rwxr-xr-x"));
        return wrapper;
    }
}
