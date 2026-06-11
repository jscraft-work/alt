package work.jscraft.alt.trading.application.ops;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchEntity;
import work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

/**
 * SELL paper 체결 시 FIFO 매칭으로 {@code paper_trade_match} row INSERT.
 *
 * <p>매칭 알고리즘:
 * <ol>
 *   <li>같은 (strategy_instance_id, symbol_code) 의 paper BUY 들을 requested_at ASC 로 조회</li>
 *   <li>각 BUY 의 잔량 = {@code buy.filled_quantity} − {@code Σ paper_trade_match.matched_quantity}</li>
 *   <li>잔량 &gt; 0 인 BUY 만 FIFO 순으로 매칭. SELL 의 잔여 수량 다 채울 때까지 multi-row 가능</li>
 *   <li>SELL 잔여가 남았는데 미매칭 BUY 가 없으면 경고 log + skip (예: 운영자가 portfolio 조작 등)</li>
 * </ol>
 *
 * <p>각 paper_trade_match row 의 PnL 메트릭:
 * <ul>
 *   <li>{@code gross_pnl_pct} = (sell.requested_price − buy.requested_price) / buy.requested_price — 의도가 기반</li>
 *   <li>{@code net_pnl_pct} = (sell_actual_pro_rata − buy_actual_pro_rata) / buy_actual_pro_rata — paper_actual_amount 의
 *       매칭 비율 환산. 슬리피지 + 매도세 + 수수료 차감 후 실제 현금 흐름</li>
 *   <li>cost 메트릭 (slippage_buy/sell_pct, sell_tax_pct, fee_pct) — buy/sell 각자 paper_*_amount 의 ratio</li>
 * </ul>
 */
@Service
public class PaperTradeMatcher {

    private static final Logger log = LoggerFactory.getLogger(PaperTradeMatcher.class);
    private static final String SIDE_BUY = "BUY";
    private static final String EXECUTION_MODE_PAPER = "paper";
    private static final int PCT_SCALE = 6;

    private final TradeOrderRepository tradeOrderRepository;
    private final PaperTradeMatchRepository paperTradeMatchRepository;

    public PaperTradeMatcher(
            TradeOrderRepository tradeOrderRepository,
            PaperTradeMatchRepository paperTradeMatchRepository) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.paperTradeMatchRepository = paperTradeMatchRepository;
    }

    @Transactional
    public void onSellFilled(TradeOrderEntity sellOrder) {
        if (sellOrder == null || sellOrder.getFilledQuantity() == null
                || sellOrder.getFilledQuantity().signum() <= 0) {
            log.debug("paper_trade_match: sellOrder filledQuantity 0 — skip");
            return;
        }

        String symbolCode = sellOrder.getTradeOrderIntent().getSymbolCode();
        java.util.UUID instanceId = sellOrder.getStrategyInstance().getId();

        List<TradeOrderEntity> candidateBuys = tradeOrderRepository
                .findByStrategyInstance_IdAndExecutionModeAndTradeOrderIntent_SideAndTradeOrderIntent_SymbolCodeOrderByRequestedAtAsc(
                        instanceId, EXECUTION_MODE_PAPER, SIDE_BUY, symbolCode);

        BigDecimal remainingSell = sellOrder.getFilledQuantity();
        int matchesCreated = 0;
        for (TradeOrderEntity buy : candidateBuys) {
            if (remainingSell.signum() <= 0) {
                break;
            }
            BigDecimal alreadyMatched = paperTradeMatchRepository.sumMatchedQuantityByBuyOrderId(buy.getId());
            if (alreadyMatched == null) {
                alreadyMatched = BigDecimal.ZERO;
            }
            BigDecimal buyRemaining = buy.getFilledQuantity().subtract(alreadyMatched);
            if (buyRemaining.signum() <= 0) {
                continue;
            }

            BigDecimal matchedQuantity = buyRemaining.min(remainingSell);

            PaperTradeMatchEntity match = buildMatch(buy, sellOrder, matchedQuantity);
            paperTradeMatchRepository.saveAndFlush(match);
            remainingSell = remainingSell.subtract(matchedQuantity);
            matchesCreated++;
        }

        if (remainingSell.signum() > 0) {
            log.warn("paper_trade_match: SELL 잔여 {} 주가 매칭되지 않음 instance={} symbol={} sellOrder={}",
                    remainingSell, instanceId, symbolCode, sellOrder.getId());
        }
        log.debug("paper_trade_match: 생성 {} row instance={} symbol={} sellOrder={}",
                matchesCreated, instanceId, symbolCode, sellOrder.getId());
    }

    private PaperTradeMatchEntity buildMatch(
            TradeOrderEntity buy,
            TradeOrderEntity sell,
            BigDecimal matchedQuantity) {
        PaperTradeMatchEntity match = new PaperTradeMatchEntity();
        match.setStrategyInstance(buy.getStrategyInstance());
        match.setBuyTradeOrder(buy);
        match.setSellTradeOrder(sell);
        match.setSymbolCode(buy.getTradeOrderIntent().getSymbolCode());
        match.setMatchedQuantity(matchedQuantity);
        match.setEntryTime(buy.getFilledAt());
        match.setExitTime(sell.getFilledAt());

        long holdingMin = Duration.between(buy.getFilledAt(), sell.getFilledAt()).toMinutes();
        match.setHoldingMinutes((int) Math.max(0, holdingMin));

        BigDecimal grossPnlPct = sell.getRequestedPrice().subtract(buy.getRequestedPrice())
                .divide(buy.getRequestedPrice(), PCT_SCALE, RoundingMode.HALF_UP);
        match.setGrossPnlPct(grossPnlPct);

        // matched_ratio per buy/sell — paper_actual_amount 의 비율 환산.
        // V17 이전 레거시 주문은 paper_actual_amount 가 null 이므로 체결가×수량으로 폴백한다
        // (비용 분해 없음 → net ≈ gross). 폴백 없이 곱하면 NPE 로 SELL 트랜잭션이 통째로 롤백된다.
        BigDecimal buyMatchedRatio = matchedQuantity.divide(buy.getFilledQuantity(), PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal sellMatchedRatio = matchedQuantity.divide(sell.getFilledQuantity(), PCT_SCALE, RoundingMode.HALF_UP);

        BigDecimal buyActualProRata = actualAmount(buy).multiply(buyMatchedRatio);
        BigDecimal sellActualProRata = actualAmount(sell).multiply(sellMatchedRatio);
        BigDecimal netPnlPct = buyActualProRata.signum() == 0
                ? grossPnlPct
                : sellActualProRata.subtract(buyActualProRata)
                        .divide(buyActualProRata, PCT_SCALE, RoundingMode.HALF_UP);
        match.setNetPnlPct(netPnlPct);

        // cost 메트릭 — buy/sell paper_*_amount 의 ratio (양수 / requested base)
        match.setSlippageBuyPct(pctOf(buy.getPaperSlippageAmount(), buy.getPaperRequestedAmount()));
        match.setSlippageSellPct(pctOf(sell.getPaperSlippageAmount(), sell.getPaperRequestedAmount()));
        match.setSellTaxPct(pctOf(sell.getPaperSellTaxAmount(), sell.getPaperRequestedAmount()));
        BigDecimal totalFee = nullToZero(buy.getPaperCommissionAmount())
                .add(nullToZero(sell.getPaperCommissionAmount()));
        match.setFeePct(pctOf(totalFee, buy.getPaperRequestedAmount()));

        return match;
    }

    private static BigDecimal pctOf(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 체결 실현금액. V17 이후 주문은 {@code paper_actual_amount} 사용, V17 이전 레거시 주문은
     * 그 값이 null 이라 체결가×수량으로 근사한다(비용 미반영). requested_price/filled_quantity 는
     * 매칭 대상 주문에 항상 채워져 있다(위에서 이미 unguarded 로 사용).
     */
    private static BigDecimal actualAmount(TradeOrderEntity order) {
        if (order.getPaperActualAmount() != null) {
            return order.getPaperActualAmount();
        }
        return order.getRequestedPrice().multiply(order.getFilledQuantity());
    }
}
