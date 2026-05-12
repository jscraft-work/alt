package work.jscraft.alt.trading.application.broker;

import java.math.BigDecimal;

public record OrderStatusResult(
        String brokerOrderNo,
        String orderStatus,
        BigDecimal filledQuantity,
        BigDecimal avgFilledPrice,
        String failureReason) {

    public boolean isTerminal() {
        return PlaceOrderResult.STATUS_FILLED.equals(orderStatus)
                || PlaceOrderResult.STATUS_REJECTED.equals(orderStatus)
                || PlaceOrderResult.STATUS_FAILED.equals(orderStatus)
                || "canceled".equals(orderStatus);
    }
}
