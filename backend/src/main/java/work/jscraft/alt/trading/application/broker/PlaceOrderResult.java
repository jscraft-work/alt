package work.jscraft.alt.trading.application.broker;

import java.math.BigDecimal;

import work.jscraft.alt.trading.domain.TradeOrderStatus;

public record PlaceOrderResult(
        String clientOrderId,
        String brokerOrderNo,
        String orderStatus,
        BigDecimal filledQuantity,
        BigDecimal avgFilledPrice,
        String failureReason) {

    public static final String STATUS_ACCEPTED = TradeOrderStatus.ACCEPTED.wireValue();
    public static final String STATUS_PARTIAL = TradeOrderStatus.PARTIAL.wireValue();
    public static final String STATUS_FILLED = TradeOrderStatus.FILLED.wireValue();
    public static final String STATUS_REJECTED = TradeOrderStatus.REJECTED.wireValue();
    public static final String STATUS_FAILED = TradeOrderStatus.FAILED.wireValue();

    public boolean isTerminalFailure() {
        return TradeOrderStatus.fromWire(orderStatus).isTerminalFailure();
    }
}
