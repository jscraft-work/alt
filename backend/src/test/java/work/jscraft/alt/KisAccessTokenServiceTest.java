package work.jscraft.alt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import work.jscraft.alt.integrations.kis.KisAccessTokenService;
import work.jscraft.alt.integrations.kis.KisProperties;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.application.MarketDataException.Category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AltTestProfile
@Import({ PostgreSqlTestConfiguration.class, RedisTestConfiguration.class })
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class KisAccessTokenServiceTest {

    private static final String APP_KEY = "test-app-key-XYZ";
    private static final String APP_SECRET = "test-app-secret-ABC";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
        // 새 properties로 매 테스트 캐시 키를 비운다.
        KisAccessTokenService priming = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));
        stringRedisTemplate.delete(priming.cacheKey(APP_KEY));
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void cacheMissIssuesAndCachesToken() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/tokenP", tokenIssuer(calls, 200, tokenBody("token-aaa", 3600)));

        KisProperties props = properties(APP_KEY, APP_SECRET, baseUrl(), 5);
        KisAccessTokenService service = newService(props);

        String token = service.getAccessToken();

        assertThat(token).isEqualTo("token-aaa");
        assertThat(calls.get()).isEqualTo(1);

        String key = service.cacheKey(APP_KEY);
        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("token-aaa");
        Long ttl = stringRedisTemplate.getExpire(key);
        assertThat(ttl).isNotNull();
        long expectedMax = 3600L - props.getTokenSafetyMarginSeconds();
        assertThat(ttl).isGreaterThan(0L);
        assertThat(ttl).isLessThanOrEqualTo(expectedMax);
    }

    @Test
    void cacheHitSkipsServer() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/tokenP", tokenIssuer(calls, 200, tokenBody("server-token", 3600)));

        KisAccessTokenService service = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));
        String key = service.cacheKey(APP_KEY);
        stringRedisTemplate.opsForValue().set(key, "cached-token", Duration.ofSeconds(600));

        String token = service.getAccessToken();

        assertThat(token).isEqualTo("cached-token");
        assertThat(calls.get()).isEqualTo(0);
    }

    @Test
    void concurrentFirstCallIssuesExactlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        // 토큰 발급에 약간의 지연을 주어 동시성 충돌을 노출시킨다.
        server.createContext("/oauth2/tokenP", exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(150L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            respondJson(exchange, 200, tokenBody("concurrent-token", 3600));
        });

        KisAccessTokenService service = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));

        int threads = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicReference<String> firstToken = new AtomicReference<>();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        String token = service.getAccessToken();
                        firstToken.compareAndSet(null, token);
                        assertThat(token).isEqualTo("concurrent-token");
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(calls.get()).isEqualTo(1);
        assertThat(firstToken.get()).isEqualTo("concurrent-token");
    }

    @Test
    void invalidateForcesReissue() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/tokenP", tokenIssuer(calls, 200, tokenBody("issued-token", 3600)));

        KisAccessTokenService service = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));

        assertThat(service.getAccessToken()).isEqualTo("issued-token");
        assertThat(calls.get()).isEqualTo(1);

        service.invalidate();

        assertThat(service.getAccessToken()).isEqualTo("issued-token");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void missingAccessTokenFieldThrowsAuthFailed() {
        AtomicInteger calls = new AtomicInteger();
        String body = "{\"error_description\":\"appkey/secret invalid\",\"error_code\":\"OAUTH_TOKEN_E001\"}";
        server.createContext("/oauth2/tokenP", tokenIssuer(calls, 200, body));

        KisAccessTokenService service = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));

        assertThatThrownBy(service::getAccessToken)
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.AUTH_FAILED));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void http401ThrowsAuthFailed() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/tokenP", tokenIssuer(calls, 401, "{\"error\":\"unauthorized\"}"));

        KisAccessTokenService service = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));

        assertThatThrownBy(service::getAccessToken)
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.AUTH_FAILED));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void timeoutThrowsTimeoutCategory() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/tokenP", exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(2_500L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(200, 0);
            } catch (IOException ignored) {
                // client disconnected after timeout
            } finally {
                exchange.close();
            }
        });

        int tokenTimeoutSeconds = 1;
        KisProperties props = properties(APP_KEY, APP_SECRET, baseUrl(), tokenTimeoutSeconds);
        KisAccessTokenService service = newService(props);

        assertThatThrownBy(service::getAccessToken)
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.TIMEOUT));
        assertThat(calls.get()).isEqualTo(1);
    }

    private KisAccessTokenService newService(KisProperties props) {
        Duration timeout = Duration.ofSeconds(Math.max(1, props.getTokenTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        return new KisAccessTokenService(props, stringRedisTemplate, objectMapper, restClient);
    }

    private static KisProperties properties(String appKey, String appSecret, String baseUrl,
                                            int tokenTimeoutSeconds) {
        KisProperties p = new KisProperties();
        p.setAppKey(appKey);
        p.setAppSecret(appSecret);
        p.setBaseUrl(baseUrl);
        p.setTokenTimeoutSeconds(tokenTimeoutSeconds);
        return p;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static String tokenBody(String token, long expiresIn) {
        return "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":"
                + expiresIn + ",\"access_token_token_expired\":\"2099-12-31 23:59:59\"}";
    }

    private static HttpHandler tokenIssuer(AtomicInteger calls, int status, String body) {
        return (HttpExchange exchange) -> {
            calls.incrementAndGet();
            respondJson(exchange, status, body);
        };
    }

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
