package work.jscraft.alt.trading.application.broker;

import java.math.BigDecimal;

import work.jscraft.alt.trading.domain.TradeOrderStatus;

public record OrderStatusResult(
        String brokerOrderNo,
        String orderStatus,
        BigDecimal filledQuantity,
        BigDecimal avgFilledPrice,
        String failureReason) {

    public boolean isTerminal() {
        return TradeOrderStatus.fromWire(orderStatus).isTerminal();
    }
}
