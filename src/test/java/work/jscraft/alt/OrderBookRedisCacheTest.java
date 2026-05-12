package work.jscraft.alt;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.marketdata.OrderBookRedisCache;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.OrderBookSnapshot;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookRedisCacheTest extends CollectorIntegrationTestSupport {

    @Autowired
    private OrderBookRedisCache orderBookRedisCache;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
    }

    @Test
    void storeWritesPayloadToRedisWithTwoDayTtl() {
        OffsetDateTime capturedAt = OffsetDateTime.of(2026, 5, 11, 9, 30, 0, 0, ZoneOffset.ofHours(9));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("bid1", 80900);
        payload.put("ask1", 81000);

        orderBookRedisCache.store(new OrderBookSnapshot("005930", capturedAt, payload, "kis"));

        String stored = orderBookRedisCache.read("005930");
        assertThat(stored).isNotNull();
        assertThat(stored).contains("\"symbolCode\":\"005930\"");
        assertThat(stored).contains("\"bid1\":80900");
        assertThat(stored).contains("\"ask1\":81000");

        Long ttlSeconds = orderBookRedisCache.ttlSeconds("005930");
        assertThat(ttlSeconds).isNotNull();
        // 정확한 TTL 검증은 시계 오차로 어려우므로 2일 ± 5초 범위로 검증
        long twoDays = Duration.ofDays(2).getSeconds();
        assertThat(ttlSeconds).isBetween(twoDays - 5, twoDays + 5);
    }
}
