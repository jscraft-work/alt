package work.jscraft.alt.auth.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.auth.config.AuthProperties;

@Service
public class RedisLoginBlockService {

    private static final String FAIL_KEY_PREFIX = "auth:login_fail:";
    private static final String BLOCK_KEY_PREFIX = "auth:login_block:";

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties authProperties;
    private final TrustedProxyIpResolver trustedProxyIpResolver;

    public RedisLoginBlockService(
            StringRedisTemplate stringRedisTemplate,
            AuthProperties authProperties,
            TrustedProxyIpResolver trustedProxyIpResolver) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authProperties = authProperties;
        this.trustedProxyIpResolver = trustedProxyIpResolver;
    }

    public void assertNotBlocked(String clientIp) {
        if (isBlocked(clientIp)) {
            throw blockedException();
        }
    }

    @Transactional
    public FailureResult recordFailure(String clientIp) {
        String normalizedIp = requireValidIp(clientIp);
        String failureKey = failureKey(normalizedIp);
        Long failures = stringRedisTemplate.opsForValue().increment(failureKey);
        stringRedisTemplate.expire(failureKey, authProperties.getLoginBlockDuration());

        int failureCount = failures == null ? 0 : failures.intValue();
        boolean blocked = failureCount >= authProperties.getLoginFailureLimit();
        if (blocked) {
            stringRedisTemplate.opsForValue().set(blockKey(normalizedIp), "1", authProperties.getLoginBlockDuration());
        }

        return new FailureResult(failureCount, blocked);
    }

    @Transactional
    public void resetFailures(String clientIp) {
        String normalizedIp = requireValidIp(clientIp);
        stringRedisTemplate.delete(failureKey(normalizedIp));
    }

    @Transactional
    public void releaseBlock(String clientIp) {
        String normalizedIp = requireValidIp(clientIp);
        stringRedisTemplate.delete(failureKey(normalizedIp));
        stringRedisTemplate.delete(blockKey(normalizedIp));
    }

    public boolean isBlocked(String clientIp) {
        String normalizedIp = requireValidIp(clientIp);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(blockKey(normalizedIp)));
    }

    public int getFailureCount(String clientIp) {
        String normalizedIp = requireValidIp(clientIp);
        String value = stringRedisTemplate.opsForValue().get(failureKey(normalizedIp));
        return value == null ? 0 : Integer.parseInt(value);
    }

    public Duration getBlockTtl(String clientIp) {
        String normalizedIp = requireValidIp(clientIp);
        Long seconds = stringRedisTemplate.getExpire(blockKey(normalizedIp));
        return seconds == null || seconds < 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }

    private String requireValidIp(String clientIp) {
        if (!trustedProxyIpResolver.isValidIp(clientIp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 clientIp가 필요합니다.");
        }
        return clientIp;
    }

    private String failureKey(String clientIp) {
        return FAIL_KEY_PREFIX + clientIp;
    }

    private String blockKey(String clientIp) {
        return BLOCK_KEY_PREFIX + clientIp;
    }

    private ResponseStatusException blockedException() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 일시적으로 차단되었습니다.");
    }

    public record FailureResult(int failureCount, boolean blocked) {
    }
}
