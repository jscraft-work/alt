package work.jscraft.alt.collector.application.marketdata;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;

@Component
public class WebSocketStatusTracker {

    public static final String KEY_LAST_CONNECTED_AT = "kis:ws:last_connected_at";
    public static final String KEY_LAST_TICK_AT = "kis:ws:last_tick_at";
    public static final String KEY_LAST_DISCONNECTED_AT = "kis:ws:last_disconnected_at";
    public static final String KEY_CURRENT_STATE = "kis:ws:state";

    public static final Duration DELAYED_THRESHOLD = Duration.ofMinutes(2);
    public static final Duration DOWN_THRESHOLD = Duration.ofMinutes(10);

    public static final String EVENT_TYPE = "marketdata.websocket.state";

    public enum WebSocketState {
        CONNECTED,
        DELAYED,
        DISCONNECTED,
        UNKNOWN
    }

    private final StringRedisTemplate redisTemplate;
    private final MarketDataOpsEventRecorder opsEventRecorder;
    private final Clock clock;

    public WebSocketStatusTracker(
            StringRedisTemplate redisTemplate,
            MarketDataOpsEventRecorder opsEventRecorder,
            Clock clock) {
        this.redisTemplate = redisTemplate;
        this.opsEventRecorder = opsEventRecorder;
        this.clock = clock;
    }

    public void recordConnected() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        redisTemplate.opsForValue().set(KEY_LAST_CONNECTED_AT, now.toString());
        redisTemplate.opsForValue().set(KEY_LAST_TICK_AT, now.toString());
        redisTemplate.opsForValue().set(KEY_CURRENT_STATE, WebSocketState.CONNECTED.name());
        opsEventRecorder.recordSuccess(MarketDataOpsEventRecorder.SERVICE_MARKETDATA, EVENT_TYPE, null);
    }

    public void recordTick() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        redisTemplate.opsForValue().set(KEY_LAST_TICK_AT, now.toString());
        redisTemplate.opsForValue().set(KEY_CURRENT_STATE, WebSocketState.CONNECTED.name());
    }

    public void recordDisconnected(String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        redisTemplate.opsForValue().set(KEY_LAST_DISCONNECTED_AT, now.toString());
        redisTemplate.opsForValue().set(KEY_CURRENT_STATE, WebSocketState.DISCONNECTED.name());
        opsEventRecorder.recordFailure(
                MarketDataOpsEventRecorder.SERVICE_MARKETDATA,
                EVENT_TYPE,
                null,
                reason,
                "WEBSOCKET_DISCONNECTED");
    }

    public WebSocketState currentState() {
        Optional<OffsetDateTime> lastDisconnected = readTimestamp(KEY_LAST_DISCONNECTED_AT);
        Optional<OffsetDateTime> lastTick = readTimestamp(KEY_LAST_TICK_AT);
        Optional<OffsetDateTime> lastConnected = readTimestamp(KEY_LAST_CONNECTED_AT);

        OffsetDateTime now = OffsetDateTime.now(clock);

        if (lastConnected.isEmpty() && lastTick.isEmpty()) {
            return WebSocketState.UNKNOWN;
        }
        if (lastDisconnected.isPresent()
                && (lastConnected.isEmpty() || lastDisconnected.get().isAfter(lastConnected.get()))) {
            return WebSocketState.DISCONNECTED;
        }
        OffsetDateTime reference = lastTick.orElseGet(lastConnected::get);
        Duration sinceTick = Duration.between(reference, now);
        if (sinceTick.compareTo(DOWN_THRESHOLD) >= 0) {
            return WebSocketState.DISCONNECTED;
        }
        if (sinceTick.compareTo(DELAYED_THRESHOLD) >= 0) {
            return WebSocketState.DELAYED;
        }
        return WebSocketState.CONNECTED;
    }

    public Optional<OffsetDateTime> readTimestamp(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
