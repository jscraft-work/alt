package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import work.jscraft.alt.trading.application.ops.SetupMetricService;
import work.jscraft.alt.trading.application.ops.SetupMetricService.DailyPnlPoint;
import work.jscraft.alt.trading.application.ops.SetupMetricService.MetricSnapshot;
import work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchEntity;
import work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SetupMetricServiceTest {

    private static final UUID INSTANCE = UUID.randomUUID();
    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-05-29T10:00:00+09:00");

    private PaperTradeMatchRepository repository;
    private SetupMetricService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaperTradeMatchRepository.class);
        service = new SetupMetricService(repository);
    }

    @Test
    void computeRecent_returnsEmpty_whenNoMatches() {
        when(repository.findByStrategyInstance_IdOrderByExitTimeDesc(eq(INSTANCE), any()))
                .thenReturn(List.of());

        MetricSnapshot snapshot = service.computeRecent(INSTANCE, 30);

        assertThat(snapshot.tradesCount()).isZero();
        assertThat(snapshot.hitRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.ev()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.pf()).isNull();
    }

    /**
     * 6 wins (+1%) + 4 losses (-0.5%) — 운영자 시연 시나리오의 paper sim 결과 모사.
     * - hitRate = 6/10 = 0.6
     * - sumProfit = 0.06, sumLoss = -0.02, ev = 0.04/10 = 0.004
     * - pf = 0.06 / 0.02 = 3.0
     * - exit_time ASC 정렬: wins 먼저 → 누적 peak 0.06, 마지막 0.04 → mdd = 0.02
     */
    @Test
    void computeRecent_tenTradesMixedWinsLosses_computesExpectedMetrics() {
        List<PaperTradeMatchEntity> matches = new ArrayList<>();
        // exit_time DESC 순 (repository 가 DESC 로 반환). losses 가 최근 → desc 에서 먼저
        for (int i = 0; i < 4; i++) {
            matches.add(buildMatch(BASE.plusMinutes(60 + i * 10), new BigDecimal("-0.005000")));
        }
        for (int i = 0; i < 6; i++) {
            matches.add(buildMatch(BASE.plusMinutes(i * 10), new BigDecimal("0.010000")));
        }

        when(repository.findByStrategyInstance_IdOrderByExitTimeDesc(eq(INSTANCE), any()))
                .thenReturn(matches);

        MetricSnapshot snapshot = service.computeRecent(INSTANCE, 30);

        assertThat(snapshot.tradesCount()).isEqualTo(10);
        assertThat(snapshot.wins()).isEqualTo(6);
        assertThat(snapshot.losses()).isEqualTo(4);
        assertThat(snapshot.hitRate()).isEqualByComparingTo("0.6000");
        assertThat(snapshot.sumProfitPct()).isEqualByComparingTo("0.060000");
        assertThat(snapshot.sumLossPct()).isEqualByComparingTo("-0.020000");
        assertThat(snapshot.ev()).isEqualByComparingTo("0.004000");
        assertThat(snapshot.pf()).isEqualByComparingTo("3.000000");
        // 누적: 0.01 → 0.02 → 0.03 → 0.04 → 0.05 → 0.06 (peak)
        //       → 0.055 → 0.05 → 0.045 → 0.04 (drawdown 0.02)
        assertThat(snapshot.mdd()).isEqualByComparingTo("0.020000");
    }

    @Test
    void computeRecent_perfectWins_pfIsNullSinceNoLosses() {
        List<PaperTradeMatchEntity> matches = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            matches.add(buildMatch(BASE.plusMinutes(i * 10), new BigDecimal("0.010000")));
        }
        when(repository.findByStrategyInstance_IdOrderByExitTimeDesc(eq(INSTANCE), any()))
                .thenReturn(matches);

        MetricSnapshot snapshot = service.computeRecent(INSTANCE, 30);

        assertThat(snapshot.wins()).isEqualTo(5);
        assertThat(snapshot.losses()).isZero();
        assertThat(snapshot.pf()).isNull();  // losses 없음 → PF undefined
        assertThat(snapshot.mdd()).isEqualByComparingTo(BigDecimal.ZERO);  // 단조 증가 → 하락 없음
    }

    @Test
    void computeDailySeries_aggregatesByKstDate() {
        List<PaperTradeMatchEntity> matches = new ArrayList<>();
        // 같은 일자 2 건 + 다음 일자 1 건
        matches.add(buildMatch(
                OffsetDateTime.parse("2026-05-29T10:00:00+09:00"), new BigDecimal("0.010000")));
        matches.add(buildMatch(
                OffsetDateTime.parse("2026-05-29T14:00:00+09:00"), new BigDecimal("-0.005000")));
        matches.add(buildMatch(
                OffsetDateTime.parse("2026-05-30T11:00:00+09:00"), new BigDecimal("0.015000")));
        when(repository.findByStrategyInstance_IdAndExitTimeBetweenOrderByExitTimeAsc(
                eq(INSTANCE), any(), any())).thenReturn(matches);

        List<DailyPnlPoint> series = service.computeDailySeries(INSTANCE, 7);

        assertThat(series).hasSize(2);
        assertThat(series.get(0).netPnlPct()).isEqualByComparingTo("0.005000"); // 0.01 - 0.005
        assertThat(series.get(1).netPnlPct()).isEqualByComparingTo("0.015000");
    }

    @Test
    void computeRecent_invalidLookback_throws() {
        assertThatThrownBy(() -> service.computeRecent(INSTANCE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.computeRecent(INSTANCE, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PaperTradeMatchEntity buildMatch(OffsetDateTime exitTime, BigDecimal netPnlPct) {
        PaperTradeMatchEntity m = new PaperTradeMatchEntity();
        m.setSymbolCode("005930");
        m.setMatchedQuantity(BigDecimal.ONE);
        m.setEntryTime(exitTime.minusMinutes(30));
        m.setExitTime(exitTime);
        m.setHoldingMinutes(30);
        m.setGrossPnlPct(netPnlPct.add(new BigDecimal("0.001000")));
        m.setNetPnlPct(netPnlPct);
        m.setSlippageBuyPct(new BigDecimal("0.000500"));
        m.setSlippageSellPct(new BigDecimal("0.000500"));
        m.setSellTaxPct(new BigDecimal("0.001500"));
        m.setFeePct(new BigDecimal("0.000280"));
        return m;
    }
}
