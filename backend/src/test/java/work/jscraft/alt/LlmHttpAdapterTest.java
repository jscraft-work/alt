package work.jscraft.alt;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import work.jscraft.alt.integrations.llm.LlmHttpAdapter;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmExecutableProperties;
import work.jscraft.alt.trading.application.decision.LlmRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmHttpAdapterTest {

    private static final String BASE_URL = "http://host.docker.internal:18000";

    private HttpClient httpClient;
    private LlmExecutableProperties properties;
    private ObjectMapper objectMapper;
    private LlmHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        properties = new LlmExecutableProperties();
        properties.setBaseUrl(BASE_URL);
        properties.getOpenclaw().setTimeoutSeconds(30);
        properties.getNanobot().setTimeoutSeconds(60);
        objectMapper = new ObjectMapper();
        adapter = new LlmHttpAdapter(properties, objectMapper, httpClient);
    }

    @Test
    void success_returnsTextFromResponse() throws Exception {
        stubResponse(200, "{\"text\":\"hello world\"}");

        LlmCallResult result = adapter.execute(request("openclaw"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdoutText()).isEqualTo("hello world");
    }

    @Test
    void requestBodyContainsCorrectLevelForProvider() throws Exception {
        stubResponse(200, "{\"text\":\"ok\"}");

        adapter.execute(request("nanobot"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(BodyHandler.class));
        HttpRequest sent = captor.getValue();
        assertThat(sent.uri().toString()).isEqualTo(BASE_URL + "/ask");
        JsonNode parsed = objectMapper.readTree(extractBody(sent));
        assertThat(parsed.get("level").asText()).isEqualTo("high");
        assertThat(parsed.get("prompt").asText()).isEqualTo("hi");
        assertThat(parsed.get("timeout_seconds").asInt()).isEqualTo(45);
    }

    @Test
    void timeout_returnsTimeoutResult() throws Exception {
        doThrow(HttpTimeoutException.class)
                .when(httpClient).send(any(HttpRequest.class), any(BodyHandler.class));

        LlmCallResult result = adapter.execute(request("openclaw"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.callStatus()).isEqualTo(LlmCallResult.CallStatus.TIMEOUT);
    }

    @Test
    void serverError_returnsNonZeroExit() throws Exception {
        stubResponse(500, "server boom");

        LlmCallResult result = adapter.execute(request("openclaw"));

        assertThat(result.callStatus()).isEqualTo(LlmCallResult.CallStatus.NON_ZERO_EXIT);
        assertThat(result.exitCode()).isEqualTo(500);
    }

    @Test
    void auth401_returnsAuthError() throws Exception {
        stubResponse(401, "unauthorized");

        LlmCallResult result = adapter.execute(request("openclaw"));

        assertThat(result.callStatus()).isEqualTo(LlmCallResult.CallStatus.AUTH_ERROR);
    }

    @Test
    void emptyText_returnsEmptyOutput() throws Exception {
        stubResponse(200, "{\"text\":\"   \"}");

        LlmCallResult result = adapter.execute(request("openclaw"));

        assertThat(result.callStatus()).isEqualTo(LlmCallResult.CallStatus.EMPTY_OUTPUT);
    }

    @Test
    void missingTextField_returnsInvalidOutput() throws Exception {
        stubResponse(200, "{\"other\":\"x\"}");

        LlmCallResult result = adapter.execute(request("openclaw"));

        assertThat(result.callStatus()).isEqualTo(LlmCallResult.CallStatus.INVALID_OUTPUT);
    }

    @Test
    void missingBaseUrl_returnsInvalidOutput() {
        properties.setBaseUrl("");
        adapter = new LlmHttpAdapter(properties, objectMapper, httpClient);

        LlmCallResult result = adapter.execute(request("openclaw"));

        assertThat(result.callStatus()).isEqualTo(LlmCallResult.CallStatus.INVALID_OUTPUT);
        assertThat(result.failureMessage()).contains("base-url");
    }

    private LlmRequest request(String provider) {
        return new LlmRequest(UUID.randomUUID(), provider, "model-x", "hi", Duration.ofSeconds(45));
    }

    private void stubResponse(int status, String body) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        doReturn(response)
                .when(httpClient).send(any(HttpRequest.class), any(BodyHandler.class));
    }

    private String extractBody(HttpRequest request) throws Exception {
        var publisher = request.bodyPublisher().orElseThrow();
        var future = new CompletableFuture<String>();
        publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
            private final StringBuilder sb = new StringBuilder();

            @Override public void onSubscribe(Flow.Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override public void onNext(ByteBuffer buf) {
                byte[] arr = new byte[buf.remaining()];
                buf.get(arr);
                sb.append(new String(arr));
            }

            @Override public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override public void onComplete() {
                future.complete(sb.toString());
            }
        });
        return future.get();
    }
}
