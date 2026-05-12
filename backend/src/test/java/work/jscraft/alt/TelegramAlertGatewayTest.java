package work.jscraft.alt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import work.jscraft.alt.integrations.telegram.TelegramAlertGateway;
import work.jscraft.alt.integrations.telegram.TelegramProperties;
import work.jscraft.alt.ops.application.AlertEvent;
import work.jscraft.alt.ops.application.AlertEvent.Severity;
import work.jscraft.alt.ops.application.AlertGateway.DispatchResult;
import work.jscraft.alt.ops.application.AlertPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramAlertGatewayTest {

    private static final String TOKEN = "test-bot-token";
    private static final String CHAT_ID = "1234567";

    private HttpServer server;
    private int port;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void happyPathPostsToTelegramSendMessage() throws Exception {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicBoolean handled = new AtomicBoolean(false);
        server.createContext("/", recorder(capturedMethod, capturedPath, capturedBody, handled, 200, "{\"ok\":true}", false, null));

        TelegramAlertGateway gateway = newGateway(enabledPolicy(), props(TOKEN, CHAT_ID, baseUrl(), 5));

        DispatchResult result = gateway.dispatch(event(Severity.WARNING, "hello", "world"));

        assertThat(result).isEqualTo(DispatchResult.SENT);
        assertThat(handled.get()).isTrue();
        assertThat(capturedMethod.get()).isEqualTo("POST");
        assertThat(capturedPath.get()).isEqualTo("/bot" + TOKEN + "/sendMessage");
        assertThat(capturedBody.get()).contains("\"chat_id\"").contains(CHAT_ID);
        assertThat(capturedBody.get()).contains("\"text\"").contains("[WARNING] hello").contains("world");
    }

    @Test
    void serverErrorReturnsFailedWithoutThrowing() {
        AtomicBoolean handled = new AtomicBoolean(false);
        server.createContext("/", exchange -> {
            handled.set(true);
            byte[] payload = "{\"ok\":false}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });

        TelegramAlertGateway gateway = newGateway(enabledPolicy(), props(TOKEN, CHAT_ID, baseUrl(), 5));

        DispatchResult result = gateway.dispatch(event(Severity.CRITICAL, "boom", "down"));

        assertThat(result).isEqualTo(DispatchResult.FAILED);
        assertThat(handled.get()).isTrue();
    }

    @Test
    void hangingServerTriggersTimeoutAndReturnsFailed() {
        AtomicBoolean handled = new AtomicBoolean(false);
        server.createContext("/", exchange -> {
            handled.set(true);
            try {
                Thread.sleep(3_000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(200, 0);
            } catch (IOException ignored) {
                // client likely gone after timeout
            } finally {
                exchange.close();
            }
        });

        int timeoutSeconds = 1;
        TelegramAlertGateway gateway = newGateway(enabledPolicy(), props(TOKEN, CHAT_ID, baseUrl(), timeoutSeconds));

        Instant start = Instant.now();
        DispatchResult result = gateway.dispatch(event(Severity.WARNING, "slow", "server"));
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(result).isEqualTo(DispatchResult.FAILED);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(timeoutSeconds + 1));
    }

    @Test
    void blankBotTokenReturnsFailedWithoutHttpCall() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        TelegramAlertGateway gateway = newGateway(enabledPolicy(), props("", CHAT_ID, baseUrl(), 5));

        DispatchResult result = gateway.dispatch(event(Severity.WARNING, "no token", "still no token"));

        assertThat(result).isEqualTo(DispatchResult.FAILED);
        assertThat(calls.get()).isZero();
    }

    @Test
    void disabledPolicyReturnsSkippedDisabledWithoutHttpCall() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        AlertPolicy disabled = new AlertPolicy();
        disabled.setEnabled(false);
        TelegramAlertGateway gateway = newGateway(disabled, props(TOKEN, CHAT_ID, baseUrl(), 5));

        DispatchResult result = gateway.dispatch(event(Severity.CRITICAL, "off", "off"));

        assertThat(result).isEqualTo(DispatchResult.SKIPPED_DISABLED);
        assertThat(calls.get()).isZero();
    }

    private TelegramAlertGateway newGateway(AlertPolicy policy, TelegramProperties properties) {
        return new TelegramAlertGateway(policy, clock, properties);
    }

    private static AlertPolicy enabledPolicy() {
        AlertPolicy policy = new AlertPolicy();
        policy.setEnabled(true);
        return policy;
    }

    private static TelegramProperties props(String token, String chatId, String baseUrl, int timeoutSeconds) {
        TelegramProperties p = new TelegramProperties();
        p.setBotToken(token);
        p.setChatId(chatId);
        p.setBaseUrl(baseUrl);
        p.setTimeoutSeconds(timeoutSeconds);
        return p;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private AlertEvent event(Severity severity, String title, String message) {
        return new AlertEvent(severity, title, message, OffsetDateTime.now(clock), Map.of());
    }

    private static HttpHandler recorder(AtomicReference<String> capturedMethod,
                                        AtomicReference<String> capturedPath,
                                        AtomicReference<String> capturedBody,
                                        AtomicBoolean handled,
                                        int status,
                                        String responseBody,
                                        boolean delay,
                                        Duration delayDuration) {
        return (HttpExchange exchange) -> {
            handled.set(true);
            capturedMethod.set(exchange.getRequestMethod());
            capturedPath.set(exchange.getRequestURI().getPath());
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            capturedBody.set(new String(requestBytes, StandardCharsets.UTF_8));
            if (delay && delayDuration != null) {
                try {
                    Thread.sleep(delayDuration.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        };
    }
}
