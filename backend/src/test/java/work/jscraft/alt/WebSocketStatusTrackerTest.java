package work.jscraft.alt;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import work.jscraft.alt.collector.application.marketdata.WebSocketStatusTracker;
import work.jscraft.alt.collector.application.marketdata.WebSocketStatusTracker.WebSocketState;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketStatusTrackerTest extends CollectorIntegrationTestSupport {

    @Autowired
    private WebSocketStatusTracker tracker;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
    }

    @Test
    void connectedThenTickKeepsStateOk() {
        tracker.recordConnected();
        assertThat(tracker.currentState()).isEqualTo(WebSocketState.CONNECTED);
        assertThat(redisTemplate.opsForValue().get(WebSocketStatusTracker.KEY_LAST_CONNECTED_AT)).isNotNull();
        assertThat(redisTemplate.opsForValue().get(WebSocketStatusTracker.KEY_LAST_TICK_AT)).isNotNull();
        assertThat(redisTemplate.opsForValue().get(WebSocketStatusTracker.KEY_CURRENT_STATE))
                .isEqualTo(WebSocketState.CONNECTED.name());

        mutableClock.advanceSeconds(30);
        tracker.recordTick();
        assertThat(tracker.currentState()).isEqualTo(WebSocketState.CONNECTED);
    }

    @Test
    void tickStaleBeyondThresholdSwitchesToDelayedThenDown() {
        tracker.recordConnected();

        mutableClock.advanceSeconds(WebSocketStatusTracker.DELAYED_THRESHOLD.toSeconds() + 5);
        assertThat(tracker.currentState()).isEqualTo(WebSocketState.DELAYED);

        mutableClock.advanceSeconds(WebSocketStatusTracker.DOWN_THRESHOLD.toSeconds()
                - WebSocketStatusTracker.DELAYED_THRESHOLD.toSeconds());
        assertThat(tracker.currentState()).isEqualTo(WebSocketState.DISCONNECTED);
    }

    @Test
    void recordDisconnectedMarksStateAsDisconnected() {
        tracker.recordConnected();

        mutableClock.advanceSeconds(10);
        tracker.recordDisconnected("network reset");

        assertThat(redisTemplate.opsForValue().get(WebSocketStatusTracker.KEY_LAST_DISCONNECTED_AT)).isNotNull();
        assertThat(redisTemplate.opsForValue().get(WebSocketStatusTracker.KEY_CURRENT_STATE))
                .isEqualTo(WebSocketState.DISCONNECTED.name());
        assertThat(tracker.currentState()).isEqualTo(WebSocketState.DISCONNECTED);
    }

    @Test
    void unknownStateBeforeAnyEvent() {
        assertThat(tracker.currentState()).isEqualTo(WebSocketState.UNKNOWN);
    }

    @Test
    void delayedThresholdHonored() {
        assertThat(WebSocketStatusTracker.DELAYED_THRESHOLD).isEqualTo(Duration.ofMinutes(2));
        assertThat(WebSocketStatusTracker.DOWN_THRESHOLD).isEqualTo(Duration.ofMinutes(10));
    }
}
