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

import work.jscraft.alt.integrations.kis.KisProperties;
import work.jscraft.alt.integrations.kis.websocket.KisApprovalKeyService;
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
class KisApprovalKeyServiceTest {

    private static final String APP_KEY = "test-app-key-WS-XYZ";
    private static final String APP_SECRET = "test-app-secret-WS-ABC";

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
        // 매 테스트마다 Redis 캐시 키를 비운다.
        KisApprovalKeyService priming = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));
        stringRedisTemplate.delete(priming.cacheKey(APP_KEY));
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void cacheMissIssuesAndCachesApprovalKey() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/Approval",
                approvalIssuer(calls, 200, approvalBody("approval-aaa")));

        KisProperties props = properties(APP_KEY, APP_SECRET, baseUrl(), 5);
        KisApprovalKeyService service = newService(props);

        String key = service.getApprovalKey();

        assertThat(key).isEqualTo("approval-aaa");
        assertThat(calls.get()).isEqualTo(1);

        String redisKey = service.cacheKey(APP_KEY);
        assertThat(stringRedisTemplate.opsForValue().get(redisKey)).isEqualTo("approval-aaa");
        Long ttl = stringRedisTemplate.getExpire(redisKey);
        assertThat(ttl).isNotNull();
        long twelveHoursMinusMargin =
                Duration.ofHours(12).getSeconds() - props.getWebsocket().getApprovalSafetyMarginSeconds();
        assertThat(ttl).isGreaterThan(0L);
        assertThat(ttl).isLessThanOrEqualTo(twelveHoursMinusMargin);
    }

    @Test
    void cacheHitSkipsServer() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/Approval",
                approvalIssuer(calls, 200, approvalBody("server-approval")));

        KisApprovalKeyService service = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));
        String redisKey = service.cacheKey(APP_KEY);
        stringRedisTemplate.opsForValue().set(redisKey, "cached-approval", Duration.ofSeconds(600));

        String key = service.getApprovalKey();

        assertThat(key).isEqualTo("cached-approval");
        assertThat(calls.get()).isEqualTo(0);
    }

    @Test
    void concurrentFirstCallIssuesExactlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/Approval", exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(150L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            respondJson(exchange, 200, approvalBody("concurrent-approval"));
        });

        KisApprovalKeyService service = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));

        int threads = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicReference<String> firstKey = new AtomicReference<>();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        String key = service.getApprovalKey();
                        firstKey.compareAndSet(null, key);
                        assertThat(key).isEqualTo("concurrent-approval");
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
        assertThat(firstKey.get()).isEqualTo("concurrent-approval");
    }

    @Test
    void invalidateForcesReissue() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/Approval",
                approvalIssuer(calls, 200, approvalBody("reissue-approval")));

        KisApprovalKeyService service = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));

        assertThat(service.getApprovalKey()).isEqualTo("reissue-approval");
        assertThat(calls.get()).isEqualTo(1);

        service.invalidate();

        assertThat(service.getApprovalKey()).isEqualTo("reissue-approval");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void missingApprovalKeyFieldThrowsAuthFailed() {
        AtomicInteger calls = new AtomicInteger();
        String body = "{\"error_description\":\"bad\",\"error_code\":\"OAUTH_E001\"}";
        server.createContext("/oauth2/Approval", approvalIssuer(calls, 200, body));

        KisApprovalKeyService service = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));

        assertThatThrownBy(service::getApprovalKey)
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.AUTH_FAILED));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void http401ThrowsAuthFailed() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/Approval",
                approvalIssuer(calls, 401, "{\"error\":\"unauthorized\"}"));

        KisApprovalKeyService service = newService(properties(APP_KEY, APP_SECRET, baseUrl(), 5));

        assertThatThrownBy(service::getApprovalKey)
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.AUTH_FAILED));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void timeoutThrowsTimeoutCategory() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/oauth2/Approval", exchange -> {
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

        int approvalTimeoutSeconds = 1;
        KisProperties props = properties(APP_KEY, APP_SECRET, baseUrl(), approvalTimeoutSeconds);
        KisApprovalKeyService service = newService(props);

        assertThatThrownBy(service::getApprovalKey)
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.TIMEOUT));
        assertThat(calls.get()).isEqualTo(1);
    }

    private KisApprovalKeyService newService(KisProperties props) {
        Duration timeout = Duration.ofSeconds(
                Math.max(1, props.getWebsocket().getApprovalTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        return new KisApprovalKeyService(props, stringRedisTemplate, objectMapper, restClient);
    }

    private static KisProperties properties(String appKey, String appSecret, String baseUrl,
                                            int approvalTimeoutSeconds) {
        KisProperties p = new KisProperties();
        p.setAppKey(appKey);
        p.setAppSecret(appSecret);
        p.setBaseUrl(baseUrl);
        p.getWebsocket().setApprovalTimeoutSeconds(approvalTimeoutSeconds);
        return p;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static String approvalBody(String key) {
        return "{\"approval_key\":\"" + key + "\"}";
    }

    private static HttpHandler approvalIssuer(AtomicInteger calls, int status, String body) {
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
