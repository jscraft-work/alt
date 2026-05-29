package work.jscraft.alt.trading.application.ops;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchEntity;
import work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchRepository;

/**
 * paper_trade_match 의 직전 N 건 또는 기간별 시계열을 집계해 운영자 4 지표 + 비용 wall 실측 산출.
 *
 * <p>plan v3-minimal §1 의 PaperEvalController 가 호출. T7 작업의 주축.
 *
 * <p>지표 정의:
 * <ul>
 *   <li>hitRate = wins / tradesCount (win = net_pnl_pct &gt; 0)</li>
 *   <li>ev = 평균 net_pnl_pct (모든 trade 의 평균 수익률 — 운영자 통과 기준 #2 와 동일 임계)</li>
 *   <li>pf = sumProfitPct / |sumLossPct| (= Profit Factor)</li>
 *   <li>mdd = 누적 net_pnl_pct 시계열의 peak-to-trough 최대 하락</li>
 *   <li>avg* 비용 메트릭 = paper_trade_match 의 각 컬럼 평균</li>
 * </ul>
 */
@Service
public class SetupMetricService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int PCT_SCALE = 6;
    private static final int RATIO_SCALE = 4;

    private final PaperTradeMatchRepository paperTradeMatchRepository;

    public SetupMetricService(PaperTradeMatchRepository paperTradeMatchRepository) {
        this.paperTradeMatchRepository = paperTradeMatchRepository;
    }

    /**
     * 직전 {@code lookback} 건의 paper_trade_match 집계 — admin 의 paper-eval 화면 4 지표 + 비용 wall.
     */
    @Transactional(readOnly = true)
    public MetricSnapshot computeRecent(UUID strategyInstanceId, int lookback) {
        if (lookback <= 0) {
            throw new IllegalArgumentException("lookback 은 양수여야 합니다: " + lookback);
        }
        List<PaperTradeMatchEntity> matches = paperTradeMatchRepository
                .findByStrategyInstance_IdOrderByExitTimeDesc(strategyInstanceId, Limit.of(lookback));

        if (matches.isEmpty()) {
            return MetricSnapshot.empty();
        }

        // 누적 PnL 계산 — exit_time ASC 순으로 재정렬해 mdd 산출
        List<PaperTradeMatchEntity> ascending = new ArrayList<>(matches);
        ascending.sort((a, b) -> a.getExitTime().compareTo(b.getExitTime()));

        BigDecimal sumNet = BigDecimal.ZERO;
        BigDecimal sumProfit = BigDecimal.ZERO;
        BigDecimal sumLoss = BigDecimal.ZERO;
        int wins = 0;
        int losses = 0;

        BigDecimal sumSlipBuy = BigDecimal.ZERO;
        BigDecimal sumSlipSell = BigDecimal.ZERO;
        BigDecimal sumSellTax = BigDecimal.ZERO;
        BigDecimal sumFee = BigDecimal.ZERO;
        int slipBuyCount = 0;
        int slipSellCount = 0;
        int sellTaxCount = 0;
        int feeCount = 0;

        BigDecimal cumulativeNet = BigDecimal.ZERO;
        BigDecimal peakCumulative = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (PaperTradeMatchEntity m : ascending) {
            BigDecimal net = nullSafe(m.getNetPnlPct());
            sumNet = sumNet.add(net);
            if (net.signum() > 0) {
                wins++;
                sumProfit = sumProfit.add(net);
            } else if (net.signum() < 0) {
                losses++;
                sumLoss = sumLoss.add(net);
            }

            cumulativeNet = cumulativeNet.add(net);
            if (cumulativeNet.compareTo(peakCumulative) > 0) {
                peakCumulative = cumulativeNet;
            }
            BigDecimal drawdown = peakCumulative.subtract(cumulativeNet);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }

            if (m.getSlippageBuyPct() != null) {
                sumSlipBuy = sumSlipBuy.add(m.getSlippageBuyPct());
                slipBuyCount++;
            }
            if (m.getSlippageSellPct() != null) {
                sumSlipSell = sumSlipSell.add(m.getSlippageSellPct());
                slipSellCount++;
            }
            if (m.getSellTaxPct() != null) {
                sumSellTax = sumSellTax.add(m.getSellTaxPct());
                sellTaxCount++;
            }
            if (m.getFeePct() != null) {
                sumFee = sumFee.add(m.getFeePct());
                feeCount++;
            }
        }

        int tradesCount = ascending.size();
        BigDecimal ev = sumNet.divide(BigDecimal.valueOf(tradesCount), PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal hitRate = BigDecimal.valueOf(wins)
                .divide(BigDecimal.valueOf(tradesCount), RATIO_SCALE, RoundingMode.HALF_UP);
        BigDecimal pf = sumLoss.signum() < 0
                ? sumProfit.divide(sumLoss.abs(), PCT_SCALE, RoundingMode.HALF_UP)
                : null; // losses 없음 → PF undefined

        BigDecimal avgSlipBuy = avg(sumSlipBuy, slipBuyCount);
        BigDecimal avgSlipSell = avg(sumSlipSell, slipSellCount);
        BigDecimal avgSellTax = avg(sumSellTax, sellTaxCount);
        BigDecimal avgFee = avg(sumFee, feeCount);

        // 비용 wall 합 = |slip_buy| + |slip_sell| + sell_tax + fee 의 평균
        // (signed slippage 가 음수면 운 좋음 — abs 로 cost 영향만 보기)
        BigDecimal avgCostTotal = abs(avgSlipBuy)
                .add(abs(avgSlipSell))
                .add(nullToZero(avgSellTax))
                .add(nullToZero(avgFee));

        return new MetricSnapshot(
                tradesCount, wins, losses,
                hitRate, sumProfit, sumLoss, ev, pf, maxDrawdown,
                avgSlipBuy, avgSlipSell, avgSellTax, avgFee, avgCostTotal);
    }

    /**
     * 직전 {@code limit} 건 paper_trade_match 의 slim view — admin paper-eval 페이지 하단 표.
     *
     * <p>F3 trade-history(페이지네이션 + 필터) 와 별개. 여기는 단순 read-only "최근 N건".
     */
    @Transactional(readOnly = true)
    public List<RecentMatchView> findRecentMatches(UUID strategyInstanceId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 은 양수여야 합니다: " + limit);
        }
        List<PaperTradeMatchEntity> matches = paperTradeMatchRepository
                .findByStrategyInstance_IdOrderByExitTimeDesc(strategyInstanceId, Limit.of(limit));

        List<RecentMatchView> result = new ArrayList<>(matches.size());
        for (PaperTradeMatchEntity m : matches) {
            result.add(new RecentMatchView(
                    m.getId(),
                    m.getSymbolCode(),
                    m.getEntryTime(),
                    m.getExitTime(),
                    m.getHoldingMinutes(),
                    m.getMatchedQuantity(),
                    m.getGrossPnlPct(),
                    m.getNetPnlPct(),
                    m.getSlippageBuyPct(),
                    m.getSlippageSellPct(),
                    m.getSellTaxPct(),
                    m.getFeePct()));
        }
        return result;
    }

    /**
     * 직전 {@code days} 일의 일별 net_pnl_pct 시계열 — 운영자 화면의 area chart 또는 curl /series 응답.
     * 일자 KST 기준. 매매 없는 일자는 0.
     */
    @Transactional(readOnly = true)
    public List<DailyPnlPoint> computeDailySeries(UUID strategyInstanceId, int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("days 는 양수여야 합니다: " + days);
        }
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.minusDays(days);
        List<PaperTradeMatchEntity> matches = paperTradeMatchRepository
                .findByStrategyInstance_IdAndExitTimeBetweenOrderByExitTimeAsc(strategyInstanceId, from, to);

        Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
        for (PaperTradeMatchEntity m : matches) {
            LocalDate day = m.getExitTime().atZoneSameInstant(KST).toLocalDate();
            byDay.merge(day, nullSafe(m.getNetPnlPct()), BigDecimal::add);
        }

        List<DailyPnlPoint> result = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : byDay.entrySet()) {
            result.add(new DailyPnlPoint(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static BigDecimal avg(BigDecimal sum, int count) {
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal abs(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.abs();
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public record MetricSnapshot(
            int tradesCount,
            int wins,
            int losses,
            BigDecimal hitRate,
            BigDecimal sumProfitPct,
            BigDecimal sumLossPct,
            BigDecimal ev,
            BigDecimal pf,
            BigDecimal mdd,
            BigDecimal avgSlippageBuyPct,
            BigDecimal avgSlippageSellPct,
            BigDecimal avgSellTaxPct,
            BigDecimal avgFeePct,
            BigDecimal avgCostTotalPct) {

        public static MetricSnapshot empty() {
            return new MetricSnapshot(
                    0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, null, BigDecimal.ZERO,
                    null, null, null, null, BigDecimal.ZERO);
        }
    }

    public record DailyPnlPoint(
            LocalDate businessDate,
            BigDecimal netPnlPct) {
    }

    /**
     * 직전 N 건 paper_trade_match 의 slim view — admin paper-eval 페이지 하단 표 노출용.
     */
    public record RecentMatchView(
            UUID id,
            String symbolCode,
            OffsetDateTime entryTime,
            OffsetDateTime exitTime,
            int holdingMinutes,
            BigDecimal matchedQuantity,
            BigDecimal grossPnlPct,
            BigDecimal netPnlPct,
            BigDecimal slippageBuyPct,
            BigDecimal slippageSellPct,
            BigDecimal sellTaxPct,
            BigDecimal feePct) {
    }
}
