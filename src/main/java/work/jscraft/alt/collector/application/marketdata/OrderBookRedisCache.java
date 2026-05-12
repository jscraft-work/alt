package work.jscraft.alt.collector.application.marketdata;

import java.time.Duration;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import work.jscraft.alt.marketdata.application.MarketDataSnapshots.OrderBookSnapshot;

@Component
public class OrderBookRedisCache {

    public static final Duration TTL = Duration.ofDays(2);
    public static final String KEY_PREFIX = "marketdata:orderbook:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public OrderBookRedisCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void store(OrderBookSnapshot snapshot) {
        String key = key(snapshot.symbolCode());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("symbolCode", snapshot.symbolCode());
        payload.put("capturedAt", snapshot.capturedAt().toString());
        payload.put("sourceName", snapshot.sourceName());
        if (snapshot.payload() != null) {
            payload.set("payload", snapshot.payload());
        }
        String value;
        try {
            value = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("호가 스냅샷 직렬화 실패", ex);
        }
        redisTemplate.opsForValue().set(key, value, TTL);
    }

    public String read(String symbolCode) {
        return redisTemplate.opsForValue().get(key(symbolCode));
    }

    public Long ttlSeconds(String symbolCode) {
        return redisTemplate.getExpire(key(symbolCode));
    }

    public static String key(String symbolCode) {
        return KEY_PREFIX + symbolCode;
    }

    public OffsetDateTime parseCapturedAt(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(objectMapper.readTree(raw).path("capturedAt").asText());
        } catch (Exception ex) {
            return null;
        }
    }
}
