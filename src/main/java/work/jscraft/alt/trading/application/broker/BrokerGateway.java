package work.jscraft.alt.trading.application.broker;

import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;

public interface BrokerGateway {

    AccountStatus getAccountStatus(BrokerAccountEntity brokerAccount);

    PlaceOrderResult placeOrder(BrokerOrderRequest request);

    OrderStatusResult getOrderStatus(BrokerAccountEntity brokerAccount, String brokerOrderNo);
}
