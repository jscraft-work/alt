package work.jscraft.alt;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import work.jscraft.alt.collector.application.marketdata.OrderBookRedisCache;
import work.jscraft.alt.trading.application.order.PaperOrderbookWalkSimulator;
import work.jscraft.alt.trading.application.order.WalkResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperOrderbookWalkSimulatorTest {

    private OrderBookRedisCache cache;
    private ObjectMapper objectMapper;
    private PaperOrderbookWalkSimulator simulator;

    @BeforeEach
    void setUp() {
        cache = mock(OrderBookRedisCache.class);
        objectMapper = new ObjectMapper();
        simulator = new PaperOrderbookWalkSimulator(cache, objectMapper);
    }

    /**
     * (a) 1 단계에서 다 체결. KOSPI 가격대 70,000 원 (100 원 tick).
     *     ask_prices[0] = 70,000, ask_volumes[0] = 200, 요청 100 → 100 채움.
     *     평균 체결가 (전) = 70,000, 한 틱 양보 +100 → avgFilledPrice = 70,100.
     */
    @Test
    void buy_oneLevelFullyFilled_appliesOneTickConcession() {
        when(cache.read("005930")).thenReturn(buildOrderbook(
                List.of(70_000L, 70_100L, 70_200L, 70_300L, 70_400L),
                List.of(200L, 100L, 50L, 30L, 20L),
                List.of(69_900L, 69_800L, 69_700L, 69_600L, 69_500L),
                List.of(150L, 100L, 80L, 60L, 40L)));

        WalkResult result = simulator.simulateBuy("005930", new BigDecimal("100"));

        assertThat(result.referencePrice()).isEqualByComparingTo("70000");
        assertThat(result.walkLevels()).isEqualTo(1);
        assertThat(result.filledQuantity()).isEqualByComparingTo("100");
        assertThat(result.unfilledQuantity()).isEqualByComparingTo("0");
        assertThat(result.partialFillRatio()).isEqualByComparingTo("1.0");
        // avgBeforeTick = 70_000, tick=100, BUY avgFilled = 70_100
        assertThat(result.avgFilledPrice()).isEqualByComparingTo("70100");
        // slippage = (70100 - 70000) × 100 = 10000 원 (디버깅용)
        assertThat(result.slippageAmount()).isEqualByComparingTo("10000");
        // actualGross = 70100 × 100 = 7_010_000
        assertThat(result.actualGrossAmount()).isEqualByComparingTo("7010000");
        assertThat(result.isPartialFill()).isFalse();
        assertThat(result.isFullyUnfilled()).isFalse();
    }

    /**
     * (b) 3 단계 walk 후 완전 체결. requestedQuantity 150, ask_volumes=[50,60,80,...].
     *     walk: 50 (70_000) + 60 (70_100) + 40 (70_200) = 150 주, walkLevels=3.
     *     cumulativeAmount = 50×70000 + 60×70100 + 40×70200 = 3_500_000 + 4_206_000 + 2_808_000 = 10_514_000
     *     avgBeforeTick = 10_514_000 / 150 = 70093.333333
     *     KrxTickSize(70093) = 100 (50,000~200,000 구간), avgFilled = 70093.333333 + 100 = 70193.333333
     */
    @Test
    void buy_threeLevelWalk_weightedAverageWithTickConcession() {
        when(cache.read("005930")).thenReturn(buildOrderbook(
                List.of(70_000L, 70_100L, 70_200L, 70_300L, 70_400L),
                List.of(50L, 60L, 80L, 30L, 20L),
                List.of(69_900L, 69_800L, 69_700L, 69_600L, 69_500L),
                List.of(150L, 100L, 80L, 60L, 40L)));

        WalkResult result = simulator.simulateBuy("005930", new BigDecimal("150"));

        assertThat(result.referencePrice()).isEqualByComparingTo("70000");
        assertThat(result.walkLevels()).isEqualTo(3);
        assertThat(result.filledQuantity()).isEqualByComparingTo("150");
        assertThat(result.unfilledQuantity()).isEqualByComparingTo("0");
        assertThat(result.partialFillRatio()).isEqualByComparingTo("1.0");
        // avgBeforeTick = 10_514_000 / 150 = 70093.333333
        // 한 틱 양보 +100 → avgFilled = 70193.333333
        assertThat(result.avgFilledPrice()).isEqualByComparingTo("70193.333333");
        // walk_path 3 단계 + 사용량 검증 (orderbookSnapshotJson)
        JsonNode snapshot = result.orderbookSnapshotJson();
        assertThat(snapshot.path("walk_path").size()).isEqualTo(3);
        assertThat(snapshot.path("walk_path").get(0).path("used_qty").asInt()).isEqualTo(50);
        assertThat(snapshot.path("walk_path").get(1).path("used_qty").asInt()).isEqualTo(60);
        assertThat(snapshot.path("walk_path").get(2).path("used_qty").asInt()).isEqualTo(40);
    }

    /**
     * (c) 5 단계 walk 후 부분체결. requestedQuantity 500, ask_volumes=[50,40,30,20,10] 합 150 → unfilled 350.
     */
    @Test
    void buy_fiveLevelWalk_partialFillUnfilledQuantityReported() {
        when(cache.read("005930")).thenReturn(buildOrderbook(
                List.of(70_000L, 70_100L, 70_200L, 70_300L, 70_400L),
                List.of(50L, 40L, 30L, 20L, 10L),
                List.of(69_900L, 69_800L, 69_700L, 69_600L, 69_500L),
                List.of(150L, 100L, 80L, 60L, 40L)));

        WalkResult result = simulator.simulateBuy("005930", new BigDecimal("500"));

        assertThat(result.walkLevels()).isEqualTo(5);
        assertThat(result.filledQuantity()).isEqualByComparingTo("150");   // 50+40+30+20+10
        assertThat(result.unfilledQuantity()).isEqualByComparingTo("350"); // 500-150
        // partialFillRatio = 150 / 500 = 0.3000
        assertThat(result.partialFillRatio()).isEqualByComparingTo("0.3000");
        assertThat(result.isPartialFill()).isTrue();
        assertThat(result.isFullyUnfilled()).isFalse();
    }

    /**
     * SELL — 한 틱 양보가 -1 tick 인지 검증. requestedQuantity 100, bid_volumes[0]=150.
     */
    @Test
    void sell_oneLevelFullyFilled_negativeTickConcession() {
        when(cache.read("005930")).thenReturn(buildOrderbook(
                List.of(70_000L, 70_100L, 70_200L, 70_300L, 70_400L),
                List.of(200L, 100L, 50L, 30L, 20L),
                List.of(69_900L, 69_800L, 69_700L, 69_600L, 69_500L),
                List.of(150L, 100L, 80L, 60L, 40L)));

        WalkResult result = simulator.simulateSell("005930", new BigDecimal("100"));

        assertThat(result.referencePrice()).isEqualByComparingTo("69900");
        assertThat(result.walkLevels()).isEqualTo(1);
        assertThat(result.filledQuantity()).isEqualByComparingTo("100");
        // avgBeforeTick = 69_900, tick=100 (200,000 미만 구간), SELL avgFilled = 69_900 - 100 = 69_800
        assertThat(result.avgFilledPrice()).isEqualByComparingTo("69800");
        // slippage = (69900 - 69800) × 100 = 10_000
        assertThat(result.slippageAmount()).isEqualByComparingTo("10000");
    }

    /**
     * KIS 가 호가 1 단계에 가격 0 (또는 잔량 0) 으로 push 하는 비정상 케이스 — walker 가 그 단계 skip 하고
     * 다음 단계 walk 진행. 운영자 요청 보너스 케이스.
     */
    @Test
    void buy_invalidPriceAtLevel1_skipsAndContinuesToNextLevel() {
        // ask_prices[0]=0 (비정상) → skip. 2 단계 50 + 3 단계 30 + 4 단계 20 = 100 → walkLevels=4 (1 단계 포함, used_qty=0)
        when(cache.read("005930")).thenReturn(buildOrderbook(
                List.of(0L, 70_100L, 70_200L, 70_300L, 70_400L),
                List.of(100L, 50L, 30L, 20L, 10L),
                List.of(69_900L, 69_800L, 69_700L, 69_600L, 69_500L),
                List.of(150L, 100L, 80L, 60L, 40L)));

        WalkResult result = simulator.simulateBuy("005930", new BigDecimal("100"));

        // 1 단계 skip + 2~4 단계 walk → walkLevels=4 (1 단계 visit 했음, used_qty=0)
        assertThat(result.walkLevels()).isEqualTo(4);
        assertThat(result.filledQuantity()).isEqualByComparingTo("100");  // 50+30+20
        assertThat(result.unfilledQuantity()).isEqualByComparingTo("0");
        // referencePrice = ask_prices[0] = 0 (정직히 반영. PaperOrderExecutor 가 0 인지 별도 검증)
        assertThat(result.referencePrice()).isEqualByComparingTo("0");
        // walk_path 4 단계 기록 + level 1 의 used_qty=0
        JsonNode snapshot = result.orderbookSnapshotJson();
        assertThat(snapshot.path("walk_path").size()).isEqualTo(4);
        assertThat(snapshot.path("walk_path").get(0).path("used_qty").asInt()).isEqualTo(0);
        assertThat(snapshot.path("walk_path").get(1).path("used_qty").asInt()).isEqualTo(50);
        assertThat(snapshot.path("walk_path").get(2).path("used_qty").asInt()).isEqualTo(30);
        assertThat(snapshot.path("walk_path").get(3).path("used_qty").asInt()).isEqualTo(20);
    }

    @Test
    void noOrderbookCache_throws() {
        when(cache.read("005930")).thenReturn(null);
        assertThatThrownBy(() -> simulator.simulateBuy("005930", new BigDecimal("100")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("호가 캐시 없음");
    }

    @Test
    void invalidRequestedQuantity_throws() {
        assertThatThrownBy(() -> simulator.simulateBuy("005930", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> simulator.simulateBuy("005930", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> simulator.simulateSell("005930", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String buildOrderbook(
            List<Long> askPrices, List<Long> askVolumes,
            List<Long> bidPrices, List<Long> bidVolumes) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("symbolCode", "005930");
        envelope.put("capturedAt", "2026-05-29T10:30:00+09:00");
        envelope.put("sourceName", "kis-ws");

        ObjectNode payload = envelope.putObject("payload");
        ArrayNode askPricesNode = payload.putArray("ask_prices");
        askPrices.forEach(askPricesNode::add);
        ArrayNode bidPricesNode = payload.putArray("bid_prices");
        bidPrices.forEach(bidPricesNode::add);
        ArrayNode askVolumesNode = payload.putArray("ask_volumes");
        askVolumes.forEach(askVolumesNode::add);
        ArrayNode bidVolumesNode = payload.putArray("bid_volumes");
        bidVolumes.forEach(bidVolumesNode::add);

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
