package work.jscraft.alt.integrations.kis.websocket;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import work.jscraft.alt.integrations.kis.KisProperties;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.application.MarketDataException.Category;

@Component
public class KisApprovalKeyService {

    static final String VENDOR = "kis";
    private static final Logger log = LoggerFactory.getLogger(KisApprovalKeyService.class);
    private static final String APPROVAL_PATH = "/oauth2/Approval";
    private static final long MIN_TTL_SECONDS = 60L;
    private static final long APPROVAL_KEY_TTL_SECONDS = Duration.ofHours(12).toSeconds();

    private final KisProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    // 단일 인스턴스 JVM 락. 분산 락(다중 인스턴스)은 후속 task에서 처리한다.
    private final ReentrantLock issueLock = new ReentrantLock();

    @Autowired
    public KisApprovalKeyService(KisProperties properties,
                                 StringRedisTemplate stringRedisTemplate,
                                 ObjectMapper objectMapper) {
        this(properties, stringRedisTemplate, objectMapper, buildRestClient(properties));
    }

    public KisApprovalKeyService(KisProperties properties,
                                 StringRedisTemplate stringRedisTemplate,
                                 ObjectMapper objectMapper,
                                 RestClient restClient) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(KisProperties properties) {
        Duration timeout = Duration.ofSeconds(
                Math.max(1, properties.getWebsocket().getApprovalTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public String getApprovalKey() {
        String appKey = properties.getAppKey();
        String appSecret = properties.getAppSecret();
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new MarketDataException(Category.AUTH_FAILED, VENDOR, "KIS appkey/appsecret 미설정");
        }
        String key = cacheKey(appKey);
        String cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        issueLock.lock();
        try {
            String recheck = readCache(key);
            if (recheck != null) {
                return recheck;
            }
            return issueAndCache(key, appKey, appSecret);
        } finally {
            issueLock.unlock();
        }
    }

    public void invalidate() {
        String appKey = properties.getAppKey();
        if (appKey == null || appKey.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(cacheKey(appKey));
        } catch (RuntimeException ex) {
            log.warn("kis approval cache delete failed: {}", ex.getClass().getSimpleName());
        }
    }

    public String cacheKey(String appKey) {
        return properties.getWebsocket().getApprovalCacheKeyPrefix() + sha256Prefix(appKey);
    }

    private String readCache(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (RuntimeException ex) {
            log.warn("kis approval cache read failed: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    private String issueAndCache(String key, String appKey, String appSecret) {
        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "appkey", appKey,
                "secretkey", appSecret);

        ResponseEntity<String> response;
        try {
            response = restClient.post()
                    .uri(properties.getBaseUrl() + APPROVAL_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
        } catch (HttpClientErrorException ex) {
            throw new MarketDataException(Category.AUTH_FAILED, VENDOR,
                    "KIS approval_key 발급 4xx 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (HttpServerErrorException ex) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS approval_key 발급 5xx 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (RestClientResponseException ex) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS approval_key 발급 비정상 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw classifyIoException(ex);
        } catch (MarketDataException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS approval_key 발급 실패: " + ex.getClass().getSimpleName(), ex);
        }

        HttpStatusCode statusCode = response.getStatusCode();
        if (!statusCode.is2xxSuccessful()) {
            throw new MarketDataException(Category.AUTH_FAILED, VENDOR,
                    "KIS approval_key 발급 HTTP 비정상 응답 status=" + statusCode.value());
        }

        String responseBody = response.getBody();
        if (responseBody == null || responseBody.isBlank()) {
            throw new MarketDataException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS approval_key 응답 본문이 비어 있다");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException ex) {
            throw new MarketDataException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS approval_key 응답 JSON 파싱 실패", ex);
        }

        JsonNode approvalNode = root.get("approval_key");
        if (approvalNode == null || approvalNode.isNull() || approvalNode.asText("").isBlank()) {
            String errorCode = textOrEmpty(root.get("error_code"));
            String errorDescription = textOrEmpty(root.get("error_description"));
            throw new MarketDataException(Category.AUTH_FAILED, VENDOR,
                    "KIS approval_key 발급 실패 error_code=" + errorCode
                            + " message=" + errorDescription);
        }
        String approvalKey = approvalNode.asText();
        long ttl = Math.max(MIN_TTL_SECONDS,
                APPROVAL_KEY_TTL_SECONDS - properties.getWebsocket().getApprovalSafetyMarginSeconds());
        try {
            stringRedisTemplate.opsForValue().set(key, approvalKey, Duration.ofSeconds(ttl));
        } catch (RuntimeException ex) {
            log.warn("kis approval cache write failed: {}", ex.getClass().getSimpleName());
        }
        log.info("KIS WebSocket approval_key 발급 완료");
        return approvalKey;
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private static MarketDataException classifyIoException(ResourceAccessException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return new MarketDataException(Category.TIMEOUT, VENDOR,
                        "KIS approval_key 발급 타임아웃", ex);
            }
            cause = cause.getCause();
        }
        return new MarketDataException(Category.TRANSIENT, VENDOR,
                "KIS approval_key 발급 네트워크 오류", ex);
    }

    private static String sha256Prefix(String appKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(appKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
