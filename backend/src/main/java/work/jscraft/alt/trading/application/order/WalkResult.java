package work.jscraft.alt.trading.application.order;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 호가창 walk 시뮬레이션 결과 — paper 매수/매도 1 회 시도에 대한 reference price / 평균 체결가 / 부분체결 정보.
 *
 * <p>{@link PaperOrderbookWalkSimulator} 가 {@code OrderBookRedisCache} 의 KIS WS push snapshot 을 읽어 1~5
 * 단계 잔량 누적 walk + 한 틱 양보 (BUY=+1tick, SELL=-1tick) 를 거쳐 산출.
 *
 * <p>PaperOrderExecutor 가 V17 의 paper_* 컬럼에 적재:
 * <ul>
 *   <li>{@link #referencePrice} → MARKET 의 경우 trade_order.requested_price 채움 (LIMIT 은 intent.price 그대로)</li>
 *   <li>{@link #avgFilledPrice} → trade_order.avg_filled_price</li>
 *   <li>{@link #filledQuantity} → trade_order.filled_quantity</li>
 *   <li>{@link #unfilledQuantity} → trade_order.unfilled_quantity</li>
 *   <li>{@link #walkLevels} → trade_order.paper_walk_levels</li>
 *   <li>{@link #partialFillRatio} → trade_order.paper_partial_fill_ratio</li>
 *   <li>{@link #orderbookSnapshotJson} → trade_order.paper_orderbook_snapshot_json</li>
 * </ul>
 *
 * <p>{@link #slippageAmount}, {@link #actualGrossAmount} 는 디버깅용. V17 의 paper_slippage_amount 는
 * PaperOrderExecutor 가 intent.requestedPrice 기준 별도 계산 (한 틱 양보 + LIMIT 임의 가격 차이 모두 흡수).
 */
public record WalkResult(
        BigDecimal referencePrice,
        BigDecimal avgFilledPrice,
        BigDecimal filledQuantity,
        BigDecimal unfilledQuantity,
        int walkLevels,
        BigDecimal partialFillRatio,
        BigDecimal slippageAmount,
        BigDecimal actualGrossAmount,
        JsonNode orderbookSnapshotJson) {

    /**
     * 호가 잔량이 모두 부족해서 단 1 주도 못 채운 경우 — PaperOrderExecutor 가 trade reject 결정의 기준.
     */
    public boolean isFullyUnfilled() {
        return filledQuantity == null || filledQuantity.signum() == 0;
    }

    /**
     * 부분체결 발생 여부 (filledQuantity > 0 AND unfilledQuantity > 0).
     */
    public boolean isPartialFill() {
        return filledQuantity != null && filledQuantity.signum() > 0
                && unfilledQuantity != null && unfilledQuantity.signum() > 0;
    }
}
