package work.jscraft.alt.integrations.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmExecutableProperties;
import work.jscraft.alt.trading.application.decision.LlmRequest;
import work.jscraft.alt.trading.application.decision.TradingDecisionEngine;

/**
 * Calls the host-side LLM HTTP wrapper:
 *   POST {base-url}/ask
 *   body: { prompt, level, timeout_seconds }
 *   response: { text }
 *
 * Replaces {@code LlmSubprocessAdapter}. Same {@link TradingDecisionEngine} contract; news
 * assessment also uses this adapter via {@link #execute(LlmRequest)}.
 */
@Component
public class LlmHttpAdapter implements TradingDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(LlmHttpAdapter.class);
    private static final String ASK_PATH = "/ask";

    private final LlmExecutableProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public LlmHttpAdapter(LlmExecutableProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                // FastAPI/uvicorn은 cleartext HTTP/2 미지원. Java 기본 HTTP/2 negotiation을
                // 시도하면 wrapper가 body를 못 읽어 422/400을 떨군다.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public LlmHttpAdapter(LlmExecutableProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public LlmCallResult requestTradingDecision(LlmRequest request) {
        return execute(request);
    }

    public LlmCallResult execute(LlmRequest request) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return LlmCallResult.invalidOutput(-1, "", "", "app.llm.base-url is not configured");
        }

        String level;
        try {
            level = LlmExecutableProperties.levelOf(request.engineName());
        } catch (IllegalArgumentException ex) {
            return LlmCallResult.invalidOutput(-1, "", "", ex.getMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", request.promptText());
        body.put("level", level);
        body.put("timeout_seconds", (int) Math.max(1, request.timeout().toSeconds()));

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            return LlmCallResult.invalidOutput(-1, "", "", "request serialize failed: " + ex.getMessage());
        }

        URI endpoint = URI.create(stripTrailingSlash(baseUrl) + ASK_PATH);
        // Wrapper enforces its own timeout server-side; we add a small buffer so the HTTP client
        // does not abort before the wrapper finishes.
        Duration httpTimeout = request.timeout().plusSeconds(5);
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(httpTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException ex) {
            return LlmCallResult.timeout("HTTP timeout: " + ex.getMessage());
        } catch (java.io.IOException ex) {
            return LlmCallResult.invalidOutput(-1, "", ex.getMessage(),
                    "HTTP IO error: " + ex.getClass().getSimpleName());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return LlmCallResult.invalidOutput(-1, "", "", "HTTP interrupted");
        }

        int status = response.statusCode();
        String responseBody = response.body() != null ? response.body() : "";

        if (status == 401 || status == 403) {
            return LlmCallResult.authError(status, responseBody, "HTTP " + status);
        }
        if (status >= 400) {
            return LlmCallResult.nonZeroExit(status, responseBody, "HTTP " + status);
        }

        String text;
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            JsonNode textNode = node.get("text");
            if (textNode == null || !textNode.isTextual()) {
                return LlmCallResult.invalidOutput(status, responseBody, "",
                        "response missing 'text' field");
            }
            text = textNode.asText("");
        } catch (Exception ex) {
            return LlmCallResult.invalidOutput(status, responseBody, "",
                    "response parse failed: " + ex.getMessage());
        }

        if (text.isBlank()) {
            return LlmCallResult.emptyOutput(status, "");
        }

        log.debug("llm.http.ok status={} level={} chars={}", status, level, text.length());
        return LlmCallResult.success(status, text, "");
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
