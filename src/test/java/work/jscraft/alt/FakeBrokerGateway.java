package work.jscraft.alt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.trading.application.broker.AccountStatus;
import work.jscraft.alt.trading.application.broker.BrokerGateway;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException;
import work.jscraft.alt.trading.application.broker.BrokerOrderRequest;
import work.jscraft.alt.trading.application.broker.OrderStatusResult;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;

class FakeBrokerGateway implements BrokerGateway {

    private final List<BrokerOrderRequest> placeOrderInvocations = new ArrayList<>();
    private final Map<String, OrderStatusResult> statusByBrokerOrderNo = new HashMap<>();
    private final Map<String, BrokerGatewayException> statusFailures = new HashMap<>();
    private PlaceOrderResult nextPlaceResult;
    private BrokerGatewayException nextPlaceFailure;
    private AccountStatus nextAccountStatus;

    void resetAll() {
        placeOrderInvocations.clear();
        statusByBrokerOrderNo.clear();
        statusFailures.clear();
        nextPlaceResult = null;
        nextPlaceFailure = null;
        nextAccountStatus = null;
    }

    void primePlaceResult(PlaceOrderResult result) {
        this.nextPlaceResult = result;
        this.nextPlaceFailure = null;
    }

    void primePlaceFailure(BrokerGatewayException exception) {
        this.nextPlaceFailure = exception;
        this.nextPlaceResult = null;
    }

    void primeStatus(String brokerOrderNo, OrderStatusResult result) {
        statusByBrokerOrderNo.put(brokerOrderNo, result);
        statusFailures.remove(brokerOrderNo);
    }

    void primeStatusFailure(String brokerOrderNo, BrokerGatewayException exception) {
        statusFailures.put(brokerOrderNo, exception);
        statusByBrokerOrderNo.remove(brokerOrderNo);
    }

    void primeAccountStatus(AccountStatus status) {
        this.nextAccountStatus = status;
    }

    List<BrokerOrderRequest> placeOrderInvocations() {
        return List.copyOf(placeOrderInvocations);
    }

    @Override
    public AccountStatus getAccountStatus(BrokerAccountEntity brokerAccount) {
        if (nextAccountStatus == null) {
            throw new BrokerGatewayException(BrokerGatewayException.Category.EMPTY_RESPONSE, "fake",
                    "no fake account status");
        }
        return nextAccountStatus;
    }

    @Override
    public PlaceOrderResult placeOrder(BrokerOrderRequest request) {
        placeOrderInvocations.add(request);
        if (nextPlaceFailure != null) {
            throw nextPlaceFailure;
        }
        if (nextPlaceResult == null) {
            throw new BrokerGatewayException(BrokerGatewayException.Category.EMPTY_RESPONSE, "fake",
                    "no fake place result");
        }
        return new PlaceOrderResult(
                request.clientOrderId(),
                nextPlaceResult.brokerOrderNo(),
                nextPlaceResult.orderStatus(),
                nextPlaceResult.filledQuantity(),
                nextPlaceResult.avgFilledPrice(),
                nextPlaceResult.failureReason());
    }

    @Override
    public OrderStatusResult getOrderStatus(BrokerAccountEntity brokerAccount, String brokerOrderNo) {
        BrokerGatewayException failure = statusFailures.get(brokerOrderNo);
        if (failure != null) {
            throw failure;
        }
        OrderStatusResult result = statusByBrokerOrderNo.get(brokerOrderNo);
        if (result == null) {
            throw new BrokerGatewayException(BrokerGatewayException.Category.ORDER_NOT_FOUND, "fake",
                    "no fake status for " + brokerOrderNo);
        }
        return result;
    }
}
