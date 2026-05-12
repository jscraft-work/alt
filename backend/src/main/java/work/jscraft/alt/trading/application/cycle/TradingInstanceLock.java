package work.jscraft.alt.trading.application.cycle;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TradingInstanceLock {

    public static final Duration TTL = Duration.ofMinutes(2);
    public static final String KEY_PREFIX = "trading:cycle:lock:";

    private final StringRedisTemplate redisTemplate;

    public TradingInstanceLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(UUID instanceId, String holderId) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(instanceId), holderId, TTL);
        return Boolean.TRUE.equals(acquired);
    }

    public boolean release(UUID instanceId, String holderId) {
        String key = key(instanceId);
        String currentHolder = redisTemplate.opsForValue().get(key);
        if (currentHolder == null || !currentHolder.equals(holderId)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public Long ttlSeconds(UUID instanceId) {
        return redisTemplate.getExpire(key(instanceId));
    }

    public String currentHolder(UUID instanceId) {
        return redisTemplate.opsForValue().get(key(instanceId));
    }

    public static String key(UUID instanceId) {
        return KEY_PREFIX + instanceId;
    }
}
