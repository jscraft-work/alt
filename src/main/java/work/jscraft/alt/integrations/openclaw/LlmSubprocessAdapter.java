package work.jscraft.alt.integrations.openclaw;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmExecutableProperties;
import work.jscraft.alt.trading.application.decision.LlmRequest;
import work.jscraft.alt.trading.application.decision.TradingDecisionEngine;

@Component
public class LlmSubprocessAdapter implements TradingDecisionEngine {

    static final String ENGINE_OPENCLAW = "openclaw";
    static final String ENGINE_NANOBOT = "nanobot";

    private static final List<String> AUTH_ERROR_KEYWORDS = List.of(
            "Token refresh failed",
            "refresh_token_reused",
            "401");

    private static final List<String> STDOUT_ERROR_KEYWORDS = List.of(
            "Error calling",
            "response failed");

    private static final String NANOBOT_PREFIX = "🐈";

    private final LlmExecutableProperties properties;
    private final ObjectMapper objectMapper;

    public LlmSubprocessAdapter(LlmExecutableProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmCallResult requestTradingDecision(LlmRequest request) {
        return execute(request);
    }

    public LlmCallResult execute(LlmRequest request) {
        List<String> argv;
        try {
            argv = buildArgv(request);
        } catch (IllegalArgumentException ex) {
            return LlmCallResult.invalidOutput(-1, "", ex.getMessage(),
                    "argv 조립 실패: " + ex.getMessage());
        }

        Process process;
        try {
            process = new ProcessBuilder(argv).redirectErrorStream(false).start();
        } catch (IOException ex) {
            return LlmCallResult.invalidOutput(-1, "", ex.getMessage(),
                    "프로세스 시작 실패: " + ex.getMessage());
        }

        boolean finished;
        try {
            finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return LlmCallResult.timeout("프로세스 대기 인터럽트: " + ex.getMessage());
        }

        if (!finished) {
            process.destroyForcibly();
            return LlmCallResult.timeout("프로세스 타임아웃: " + request.timeout().toSeconds() + "s");
        }

        String rawStdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int exitCode = process.exitValue();

        if (isAuthError(stderr)) {
            return LlmCallResult.authError(exitCode, rawStdout, stderr);
        }
        if (exitCode != 0) {
            return LlmCallResult.nonZeroExit(exitCode, rawStdout, stderr);
        }

        String processedStdout = postProcessStdout(request.engineName(), rawStdout);
        if (processedStdout == null || processedStdout.isBlank()) {
            return LlmCallResult.emptyOutput(exitCode, stderr);
        }
        if (containsStdoutErrorKeyword(processedStdout)) {
            return LlmCallResult.invalidOutput(exitCode, processedStdout, stderr,
                    "stdout error keyword 감지");
        }
        return LlmCallResult.success(exitCode, processedStdout, stderr);
    }

    public List<String> buildArgv(LlmRequest req) {
        String sid = req.sessionId().toString();
        return switch (req.engineName()) {
            case ENGINE_OPENCLAW -> List.of(
                    properties.getOpenclaw().getCommand(),
                    "agent", "--local",
                    "--session-id", sid,
                    "-m", req.promptText(),
                    "--json");
            case ENGINE_NANOBOT -> List.of(
                    properties.getNanobot().getCommand(),
                    "agent", "--no-markdown",
                    "-s", sid,
                    "-m", req.promptText());
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 LLM provider: " + req.engineName());
        };
    }

    public String postProcessStdout(String engineName, String stdout) {
        if (stdout == null) {
            return "";
        }
        return switch (engineName) {
            case ENGINE_OPENCLAW -> unwrapOpenclawJson(stdout);
            case ENGINE_NANOBOT -> stripNanobotPrefix(stdout);
            default -> stdout;
        };
    }

    private String unwrapOpenclawJson(String stdout) {
        String trimmed = stdout.trim();
        if (trimmed.isEmpty()) {
            return stdout;
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            JsonNode payloads = root.path("payloads");
            if (payloads.isArray() && payloads.size() > 0) {
                JsonNode text = payloads.get(0).path("text");
                if (text.isTextual() && !text.asText().isEmpty()) {
                    return text.asText();
                }
            }
        } catch (IOException ex) {
            // raw fallback below
        }
        return stdout;
    }

    private String stripNanobotPrefix(String stdout) {
        if (!stdout.startsWith(NANOBOT_PREFIX)) {
            return stdout;
        }
        int newlineIdx = stdout.indexOf('\n');
        if (newlineIdx < 0) {
            return "";
        }
        return stdout.substring(newlineIdx + 1);
    }

    private boolean isAuthError(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return false;
        }
        return AUTH_ERROR_KEYWORDS.stream().anyMatch(stderr::contains);
    }

    private boolean containsStdoutErrorKeyword(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return false;
        }
        return STDOUT_ERROR_KEYWORDS.stream().anyMatch(stdout::contains);
    }

    private String readAll(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
