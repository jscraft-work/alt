package work.jscraft.alt.trading.application.broker;

import java.math.BigDecimal;

import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;

public record BrokerOrderRequest(
        BrokerAccountEntity brokerAccount,
        String clientOrderId,
        String symbolCode,
        String side,
        BigDecimal quantity,
        String orderType,
        BigDecimal price) {
}
