package work.jscraft.alt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import work.jscraft.alt.disclosure.application.DisclosureExternalRecords.ExternalDisclosureItem;
import work.jscraft.alt.disclosure.application.DisclosureGatewayException;
import work.jscraft.alt.disclosure.application.DisclosureGatewayException.Category;
import work.jscraft.alt.integrations.dart.DartDisclosureAdapter;
import work.jscraft.alt.integrations.dart.DartProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DartDisclosureAdapterTest {

    private static final String API_KEY = "test-api-key-0123456789-0123456789-01234";
    private static final String CORP_CODE = "00126380";

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
    void happyPathReturnsMappedItems() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        AtomicBoolean handled = new AtomicBoolean(false);
        String body = """
                {
                  "status": "000",
                  "message": "OK",
                  "page_no": 1,
                  "page_count": 100,
                  "total_count": 2,
                  "total_page": 1,
                  "list": [
                    {
                      "corp_code": "00126380",
                      "corp_name": "삼성전자",
                      "stock_code": "005930",
                      "corp_cls": "Y",
                      "report_nm": "주요사항보고서(자기주식취득결정)",
                      "rcept_no": "20240514000123",
                      "flr_nm": "삼성전자",
                      "rcept_dt": "20240514",
                      "rm": "유"
                    },
                    {
                      "corp_code": "00126380",
                      "corp_name": "삼성전자",
                      "stock_code": "005930",
                      "corp_cls": "Y",
                      "report_nm": "분기보고서",
                      "rcept_no": "20240515000999",
                      "flr_nm": "삼성전자",
                      "rcept_dt": "20240515",
                      "rm": ""
                    }
                  ]
                }
                """;
        server.createContext("/api/list.json", recorder(handled, capturedQuery, 200, body));

        DartDisclosureAdapter adapter = newAdapter(properties(API_KEY, baseUrl(), 5));

        List<ExternalDisclosureItem> items = adapter.fetchDisclosures(CORP_CODE,
                OffsetDateTime.parse("2024-05-01T00:00:00+09:00"));

        assertThat(handled.get()).isTrue();
        String query = capturedQuery.get();
        assertThat(query).contains("crtfc_key=" + API_KEY);
        assertThat(query).contains("corp_code=" + CORP_CODE);
        assertThat(query).contains("bgn_de=20240501");
        assertThat(query).contains("end_de=20260511");
        assertThat(query).contains("page_count=100");
        assertThat(query).contains("page_no=1");

        assertThat(items).hasSize(2);

        ExternalDisclosureItem first = items.get(0);
        assertThat(first.dartCorpCode()).isEqualTo(CORP_CODE);
        assertThat(first.disclosureNo()).isEqualTo("20240514000123");
        assertThat(first.title()).isEqualTo("주요사항보고서(자기주식취득결정)");
        assertThat(first.previewText()).isEqualTo("유");
        assertThat(first.documentUrl())
                .isEqualTo("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20240514000123");
        assertThat(first.publishedAt())
                .isEqualTo(OffsetDateTime.of(2024, 5, 14, 0, 0, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(first.publishedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));

        ExternalDisclosureItem second = items.get(1);
        assertThat(second.disclosureNo()).isEqualTo("20240515000999");
        assertThat(second.title()).isEqualTo("분기보고서");
        assertThat(second.previewText()).isNull();
        assertThat(second.publishedAt())
                .isEqualTo(OffsetDateTime.of(2024, 5, 15, 0, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void status013ReturnsEmptyListWithoutThrowing() {
        AtomicBoolean handled = new AtomicBoolean(false);
        String body = "{\"status\":\"013\",\"message\":\"조회된 데이터가 없습니다.\"}";
        server.createContext("/api/list.json", recorder(handled, new AtomicReference<>(), 200, body));

        DartDisclosureAdapter adapter = newAdapter(properties(API_KEY, baseUrl(), 5));

        List<ExternalDisclosureItem> items = adapter.fetchDisclosures(CORP_CODE,
                OffsetDateTime.parse("2024-05-01T00:00:00+09:00"));

        assertThat(handled.get()).isTrue();
        assertThat(items).isEmpty();
    }

    @Test
    void status010ThrowsAuthFailed() {
        AtomicBoolean handled = new AtomicBoolean(false);
        String body = "{\"status\":\"010\",\"message\":\"등록되지 않은 키입니다.\"}";
        server.createContext("/api/list.json", recorder(handled, new AtomicReference<>(), 200, body));

        DartDisclosureAdapter adapter = newAdapter(properties(API_KEY, baseUrl(), 5));

        assertThatThrownBy(() -> adapter.fetchDisclosures(CORP_CODE,
                OffsetDateTime.parse("2024-05-01T00:00:00+09:00")))
                .isInstanceOf(DisclosureGatewayException.class)
                .satisfies(ex -> assertThat(((DisclosureGatewayException) ex).getCategory())
                        .isEqualTo(Category.AUTH_FAILED));
        assertThat(handled.get()).isTrue();
    }

    @Test
    void status900ThrowsTransient() {
        AtomicBoolean handled = new AtomicBoolean(false);
        String body = "{\"status\":\"900\",\"message\":\"정의되지 않은 오류가 발생하였습니다.\"}";
        server.createContext("/api/list.json", recorder(handled, new AtomicReference<>(), 200, body));

        DartDisclosureAdapter adapter = newAdapter(properties(API_KEY, baseUrl(), 5));

        assertThatThrownBy(() -> adapter.fetchDisclosures(CORP_CODE,
                OffsetDateTime.parse("2024-05-01T00:00:00+09:00")))
                .isInstanceOf(DisclosureGatewayException.class)
                .satisfies(ex -> assertThat(((DisclosureGatewayException) ex).getCategory())
                        .isEqualTo(Category.TRANSIENT));
        assertThat(handled.get()).isTrue();
    }

    @Test
    void malformedJsonThrowsInvalidResponse() {
        AtomicBoolean handled = new AtomicBoolean(false);
        String body = "{not valid json";
        server.createContext("/api/list.json", recorder(handled, new AtomicReference<>(), 200, body));

        DartDisclosureAdapter adapter = newAdapter(properties(API_KEY, baseUrl(), 5));

        assertThatThrownBy(() -> adapter.fetchDisclosures(CORP_CODE,
                OffsetDateTime.parse("2024-05-01T00:00:00+09:00")))
                .isInstanceOf(DisclosureGatewayException.class)
                .satisfies(ex -> assertThat(((DisclosureGatewayException) ex).getCategory())
                        .isEqualTo(Category.INVALID_RESPONSE));
        assertThat(handled.get()).isTrue();
    }

    @Test
    void timeoutThrowsTimeoutWithinDeadline() {
        AtomicBoolean handled = new AtomicBoolean(false);
        server.createContext("/api/list.json", exchange -> {
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
        DartDisclosureAdapter adapter = newAdapter(properties(API_KEY, baseUrl(), timeoutSeconds));

        Instant start = Instant.now();
        assertThatThrownBy(() -> adapter.fetchDisclosures(CORP_CODE,
                OffsetDateTime.parse("2024-05-01T00:00:00+09:00")))
                .isInstanceOf(DisclosureGatewayException.class)
                .satisfies(ex -> assertThat(((DisclosureGatewayException) ex).getCategory())
                        .isEqualTo(Category.TIMEOUT));
        Duration elapsed = Duration.between(start, Instant.now());
        assertThat(elapsed).isLessThan(Duration.ofSeconds(timeoutSeconds + 1));
    }

    @Test
    void blankApiKeyThrowsAuthFailedWithoutHttpCall() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/api/list.json", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        DartDisclosureAdapter adapter = newAdapter(properties("", baseUrl(), 5));

        assertThatThrownBy(() -> adapter.fetchDisclosures(CORP_CODE,
                OffsetDateTime.parse("2024-05-01T00:00:00+09:00")))
                .isInstanceOf(DisclosureGatewayException.class)
                .satisfies(ex -> assertThat(((DisclosureGatewayException) ex).getCategory())
                        .isEqualTo(Category.AUTH_FAILED));
        assertThat(calls.get()).isZero();
    }

    @Test
    void multiPageResponseReturnsFirstPageWithoutThrowing() {
        AtomicBoolean handled = new AtomicBoolean(false);
        String body = """
                {
                  "status": "000",
                  "message": "OK",
                  "page_no": 1,
                  "page_count": 100,
                  "total_count": 150,
                  "total_page": 2,
                  "list": [
                    {
                      "corp_code": "00126380",
                      "corp_name": "삼성전자",
                      "stock_code": "005930",
                      "corp_cls": "Y",
                      "report_nm": "분기보고서",
                      "rcept_no": "20240515000999",
                      "flr_nm": "삼성전자",
                      "rcept_dt": "20240515",
                      "rm": ""
                    }
                  ]
                }
                """;
        server.createContext("/api/list.json", recorder(handled, new AtomicReference<>(), 200, body));

        DartDisclosureAdapter adapter = newAdapter(properties(API_KEY, baseUrl(), 5));

        List<ExternalDisclosureItem> items = adapter.fetchDisclosures(CORP_CODE,
                OffsetDateTime.parse("2024-05-01T00:00:00+09:00"));

        assertThat(handled.get()).isTrue();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).disclosureNo()).isEqualTo("20240515000999");
    }

    private DartDisclosureAdapter newAdapter(DartProperties properties) {
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        return new DartDisclosureAdapter(properties, restClient, new ObjectMapper(), clock);
    }

    private static DartProperties properties(String apiKey, String baseUrl, int timeoutSeconds) {
        DartProperties p = new DartProperties();
        p.setApiKey(apiKey);
        p.setBaseUrl(baseUrl);
        p.setTimeoutSeconds(timeoutSeconds);
        return p;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static HttpHandler recorder(AtomicBoolean handled,
                                        AtomicReference<String> capturedQuery,
                                        int status,
                                        String responseBody) {
        return (HttpExchange exchange) -> {
            handled.set(true);
            capturedQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        };
    }
}
