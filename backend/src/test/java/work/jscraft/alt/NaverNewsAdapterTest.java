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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import work.jscraft.alt.integrations.naver.NaverNewsAdapter;
import work.jscraft.alt.integrations.naver.NaverProperties;
import work.jscraft.alt.news.application.NewsExternalRecords.ExternalArticleBody;
import work.jscraft.alt.news.application.NewsExternalRecords.ExternalNewsItem;
import work.jscraft.alt.news.application.NewsGatewayException;
import work.jscraft.alt.news.application.NewsGatewayException.Category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NaverNewsAdapterTest {

    private static final String CLIENT_ID = "naver-client-id";
    private static final String CLIENT_SECRET = "naver-client-secret";

    private HttpServer server;
    private int port;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-12T00:00:00Z"), ZoneId.of("Asia/Seoul"));

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

    // ------------------------------------------------------------------------------------------
    // fetchNews cases
    // ------------------------------------------------------------------------------------------

    @Test
    void fetchNewsHappyMapsItemsAndSendsCredentials() {
        AtomicBoolean handled = new AtomicBoolean(false);
        AtomicReference<String> capturedClientId = new AtomicReference<>();
        AtomicReference<String> capturedClientSecret = new AtomicReference<>();
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        String body = """
                {
                  "items": [
                    {
                      "title": "<b>삼성전자</b> 실적 &amp; 호재",
                      "originallink": "https://news.example/orig/1",
                      "link": "https://news.example/1",
                      "description": "삼성전자가 &quot;호재&quot;라는 <b>분석</b>",
                      "pubDate": "Mon, 11 May 2026 10:00:00 +0900"
                    },
                    {
                      "title": "두 번째 기사",
                      "originallink": "https://news.example/orig/2",
                      "link": "https://news.example/2",
                      "description": "요약 2",
                      "pubDate": "Mon, 11 May 2026 09:00:00 +0900"
                    }
                  ]
                }
                """;
        server.createContext("/v1/search/news.json", exchange -> {
            handled.set(true);
            capturedClientId.set(exchange.getRequestHeaders().getFirst("X-Naver-Client-Id"));
            capturedClientSecret.set(exchange.getRequestHeaders().getFirst("X-Naver-Client-Secret"));
            capturedQuery.set(exchange.getRequestURI().getRawQuery());
            respondJson(exchange, 200, body);
        });

        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        List<ExternalNewsItem> items = adapter.fetchNews("005930",
                OffsetDateTime.parse("2026-05-11T00:00:00+09:00"));

        assertThat(handled.get()).isTrue();
        assertThat(capturedClientId.get()).isEqualTo(CLIENT_ID);
        assertThat(capturedClientSecret.get()).isEqualTo(CLIENT_SECRET);
        assertThat(capturedQuery.get()).contains("display=30");
        assertThat(capturedQuery.get()).contains("sort=date");
        assertThat(capturedQuery.get()).contains("query=");

        assertThat(items).hasSize(2);
        ExternalNewsItem first = items.get(0);
        assertThat(first.providerName()).isEqualTo("naver");
        assertThat(first.externalNewsId()).isEqualTo("https://news.example/1");
        assertThat(first.title()).isEqualTo("삼성전자 실적 & 호재");
        assertThat(first.summary()).isEqualTo("삼성전자가 \"호재\"라는 분석");
        assertThat(first.articleUrl()).isEqualTo("https://news.example/1");
        assertThat(first.publishedAt())
                .isEqualTo(OffsetDateTime.of(2026, 5, 11, 10, 0, 0, 0, ZoneOffset.ofHours(9)));

        ExternalNewsItem second = items.get(1);
        assertThat(second.title()).isEqualTo("두 번째 기사");
        assertThat(second.publishedAt())
                .isEqualTo(OffsetDateTime.of(2026, 5, 11, 9, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void fetchNewsFiltersItemsOlderThanSinceInclusive() {
        AtomicBoolean handled = new AtomicBoolean(false);
        String body = """
                {
                  "items": [
                    {
                      "title": "최신",
                      "originallink": "https://news.example/orig/a",
                      "link": "https://news.example/a",
                      "description": "a",
                      "pubDate": "Mon, 11 May 2026 12:00:00 +0900"
                    },
                    {
                      "title": "중간",
                      "originallink": "https://news.example/orig/b",
                      "link": "https://news.example/b",
                      "description": "b",
                      "pubDate": "Mon, 11 May 2026 10:00:00 +0900"
                    },
                    {
                      "title": "오래된",
                      "originallink": "https://news.example/orig/c",
                      "link": "https://news.example/c",
                      "description": "c",
                      "pubDate": "Mon, 11 May 2026 08:00:00 +0900"
                    }
                  ]
                }
                """;
        server.createContext("/v1/search/news.json", exchange -> {
            handled.set(true);
            respondJson(exchange, 200, body);
        });

        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        List<ExternalNewsItem> items = adapter.fetchNews("005930",
                OffsetDateTime.parse("2026-05-11T09:00:00+09:00"));

        assertThat(handled.get()).isTrue();
        assertThat(items).hasSize(2);
        assertThat(items.stream().map(ExternalNewsItem::title)).containsExactly("최신", "중간");
    }

    @Test
    void fetchNewsUnauthorizedThrowsAuthFailed() {
        AtomicBoolean handled = new AtomicBoolean(false);
        server.createContext("/v1/search/news.json", exchange -> {
            handled.set(true);
            respondJson(exchange, 401, "{\"errorMessage\":\"unauthorized\"}");
        });

        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        assertThatThrownBy(() -> adapter.fetchNews("005930",
                OffsetDateTime.parse("2026-05-01T00:00:00+09:00")))
                .isInstanceOf(NewsGatewayException.class)
                .satisfies(ex -> assertThat(((NewsGatewayException) ex).getCategory())
                        .isEqualTo(Category.AUTH_FAILED));
        assertThat(handled.get()).isTrue();
    }

    @Test
    void fetchNewsBadRequestThrowsRequestInvalid() {
        AtomicBoolean handled = new AtomicBoolean(false);
        server.createContext("/v1/search/news.json", exchange -> {
            handled.set(true);
            respondJson(exchange, 400, "{\"errorMessage\":\"bad-request\"}");
        });

        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        assertThatThrownBy(() -> adapter.fetchNews("005930",
                OffsetDateTime.parse("2026-05-01T00:00:00+09:00")))
                .isInstanceOf(NewsGatewayException.class)
                .satisfies(ex -> assertThat(((NewsGatewayException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
        assertThat(handled.get()).isTrue();
    }

    @Test
    void fetchNewsRateLimitedThrowsTransient() {
        AtomicBoolean handled = new AtomicBoolean(false);
        server.createContext("/v1/search/news.json", exchange -> {
            handled.set(true);
            respondJson(exchange, 429, "{\"errorMessage\":\"rate-limited\"}");
        });

        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        assertThatThrownBy(() -> adapter.fetchNews("005930",
                OffsetDateTime.parse("2026-05-01T00:00:00+09:00")))
                .isInstanceOf(NewsGatewayException.class)
                .satisfies(ex -> assertThat(((NewsGatewayException) ex).getCategory())
                        .isEqualTo(Category.TRANSIENT));
        assertThat(handled.get()).isTrue();
    }

    @Test
    void fetchNewsInvalidJsonThrowsInvalidResponse() {
        AtomicBoolean handled = new AtomicBoolean(false);
        server.createContext("/v1/search/news.json", exchange -> {
            handled.set(true);
            respondJson(exchange, 200, "{not valid json");
        });

        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        assertThatThrownBy(() -> adapter.fetchNews("005930",
                OffsetDateTime.parse("2026-05-01T00:00:00+09:00")))
                .isInstanceOf(NewsGatewayException.class)
                .satisfies(ex -> assertThat(((NewsGatewayException) ex).getCategory())
                        .isEqualTo(Category.INVALID_RESPONSE));
        assertThat(handled.get()).isTrue();
    }

    @Test
    void fetchNewsTimeoutThrowsTimeoutWithinDeadline() {
        AtomicBoolean handled = new AtomicBoolean(false);
        server.createContext("/v1/search/news.json", exchange -> {
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

        int searchTimeoutSeconds = 1;
        NaverNewsAdapter adapter = newAdapter(
                properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), searchTimeoutSeconds, 5, 30));

        Instant start = Instant.now();
        assertThatThrownBy(() -> adapter.fetchNews("005930",
                OffsetDateTime.parse("2026-05-01T00:00:00+09:00")))
                .isInstanceOf(NewsGatewayException.class)
                .satisfies(ex -> assertThat(((NewsGatewayException) ex).getCategory())
                        .isEqualTo(Category.TIMEOUT));
        Duration elapsed = Duration.between(start, Instant.now());
        assertThat(elapsed).isLessThan(Duration.ofSeconds(searchTimeoutSeconds + 1));
    }

    @Test
    void fetchNewsBlankCredsThrowsAuthFailedWithoutHttpCall() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/search/news.json", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        NaverNewsAdapter adapter = newAdapter(properties("", "", baseUrl(), 5, 5, 30));

        assertThatThrownBy(() -> adapter.fetchNews("005930",
                OffsetDateTime.parse("2026-05-01T00:00:00+09:00")))
                .isInstanceOf(NewsGatewayException.class)
                .satisfies(ex -> assertThat(((NewsGatewayException) ex).getCategory())
                        .isEqualTo(Category.AUTH_FAILED));
        assertThat(calls.get()).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // fetchArticleBody cases
    // ------------------------------------------------------------------------------------------

    @Test
    void fetchArticleBodyExtractsArticleTextAndSkipsScript() {
        AtomicReference<String> capturedUserAgent = new AtomicReference<>();
        String html = """
                <html>
                <head><title>제목</title><script>var evil = "ignore me";</script></head>
                <body>
                  <header>HEADER</header>
                  <article>
                    <p>real text 본문 내용</p>
                    <script>var stealth = "evil"; document.write("evil");</script>
                    <p>두 번째 단락</p>
                  </article>
                  <footer>FOOTER</footer>
                </body>
                </html>
                """;
        server.createContext("/article/1", exchange -> {
            capturedUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            respondHtml(exchange, 200, html);
        });

        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        Optional<ExternalArticleBody> body = adapter.fetchArticleBody(baseUrl() + "/article/1");

        assertThat(body).isPresent();
        String text = body.get().bodyText();
        assertThat(text).contains("real text");
        assertThat(text).contains("두 번째 단락");
        assertThat(text).doesNotContain("evil");
        assertThat(text).doesNotContain("HEADER");
        assertThat(text).doesNotContain("FOOTER");
        assertThat(capturedUserAgent.get()).contains("Mozilla/5.0");
    }

    @Test
    void fetchArticleBodyFallsBackToBodyTextWithoutArticleTag() {
        String html = """
                <html><body>
                  <div><p>일반 본문 단락 입니다.</p></div>
                  <span>추가 문장.</span>
                  <script>doNotInclude();</script>
                </body></html>
                """;
        server.createContext("/article/2", exchange -> respondHtml(exchange, 200, html));

        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        Optional<ExternalArticleBody> body = adapter.fetchArticleBody(baseUrl() + "/article/2");

        assertThat(body).isPresent();
        String text = body.get().bodyText();
        assertThat(text).contains("일반 본문 단락 입니다.");
        assertThat(text).contains("추가 문장.");
        assertThat(text).doesNotContain("doNotInclude");
    }

    @Test
    void fetchArticleBodyReturnsEmptyWhenOnlySkippedTags() {
        String html = """
                <html><body>
                  <script>only();</script>
                  <style>body { color: red; }</style>
                  <noscript>js disabled</noscript>
                </body></html>
                """;
        server.createContext("/article/3", exchange -> respondHtml(exchange, 200, html));

        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        Optional<ExternalArticleBody> body = adapter.fetchArticleBody(baseUrl() + "/article/3");

        assertThat(body).isEmpty();
    }

    @Test
    void fetchArticleBodyFourOhFourThrowsTransient() {
        server.createContext("/article/missing", exchange -> respondHtml(exchange, 404, "<html>nope</html>"));

        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        assertThatThrownBy(() -> adapter.fetchArticleBody(baseUrl() + "/article/missing"))
                .isInstanceOf(NewsGatewayException.class)
                .satisfies(ex -> assertThat(((NewsGatewayException) ex).getCategory())
                        .isEqualTo(Category.TRANSIENT));
    }

    @Test
    void fetchArticleBodyBlankUrlThrowsRequestInvalid() {
        NaverNewsAdapter adapter = newAdapter(properties(CLIENT_ID, CLIENT_SECRET, baseUrl(), 5, 5, 30));

        assertThatThrownBy(() -> adapter.fetchArticleBody(""))
                .isInstanceOf(NewsGatewayException.class)
                .satisfies(ex -> assertThat(((NewsGatewayException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
        assertThatThrownBy(() -> adapter.fetchArticleBody(null))
                .isInstanceOf(NewsGatewayException.class)
                .satisfies(ex -> assertThat(((NewsGatewayException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
    }

    // ------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------

    private NaverNewsAdapter newAdapter(NaverProperties properties) {
        RestClient searchClient = buildClient(properties.getSearchTimeoutSeconds());
        RestClient articleClient = buildClient(properties.getArticleTimeoutSeconds());
        return new NaverNewsAdapter(properties, searchClient, articleClient, new ObjectMapper(), clock);
    }

    private static RestClient buildClient(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    private static NaverProperties properties(String clientId,
                                              String clientSecret,
                                              String baseUrl,
                                              int searchTimeoutSeconds,
                                              int articleTimeoutSeconds,
                                              int displayLimit) {
        NaverProperties p = new NaverProperties();
        p.setClientId(clientId);
        p.setClientSecret(clientSecret);
        p.setBaseUrl(baseUrl);
        p.setSearchTimeoutSeconds(searchTimeoutSeconds);
        p.setArticleTimeoutSeconds(articleTimeoutSeconds);
        p.setDisplayLimit(displayLimit);
        return p;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        if (payload.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, payload.length);
            exchange.getResponseBody().write(payload);
        }
        exchange.close();
    }

    private static void respondHtml(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "text/html; charset=utf-8");
        if (payload.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, payload.length);
            exchange.getResponseBody().write(payload);
        }
        exchange.close();
    }
}
