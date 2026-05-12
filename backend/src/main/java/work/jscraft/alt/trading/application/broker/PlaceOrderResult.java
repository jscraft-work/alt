package work.jscraft.alt.trading.application.broker;

import java.math.BigDecimal;

public record PlaceOrderResult(
        String clientOrderId,
        String brokerOrderNo,
        String orderStatus,
        BigDecimal filledQuantity,
        BigDecimal avgFilledPrice,
        String failureReason) {

    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_FILLED = "filled";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_FAILED = "failed";

    public boolean isTerminalFailure() {
        return STATUS_REJECTED.equals(orderStatus) || STATUS_FAILED.equals(orderStatus);
    }
}
