package work.jscraft.alt.trading.application.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.common.security.BrokerAccountMasker;
import work.jscraft.alt.portfolio.application.PortfolioUpdateService;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountRepository;
import work.jscraft.alt.portfolio.application.PortfolioUpdateService.PortfolioSnapshot;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.broker.BrokerGateway;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException;
import work.jscraft.alt.trading.application.broker.BrokerOrderRequest;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

@Service
public class LiveOrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(LiveOrderExecutor.class);

    public static final String EXECUTION_MODE_LIVE = "live";
    public static final String SIDE_BUY = "BUY";
    public static final String SIDE_SELL = "SELL";

    private final BrokerGateway brokerGateway;
    private final TradeOrderRepository tradeOrderRepository;
    private final PortfolioUpdateService portfolioUpdateService;
    private final BrokerAccountRepository brokerAccountRepository;
    private final Clock clock;

    public LiveOrderExecutor(
            BrokerGateway brokerGateway,
            TradeOrderRepository tradeOrderRepository,
            PortfolioUpdateService portfolioUpdateService,
            BrokerAccountRepository brokerAccountRepository,
            Clock clock) {
        this.brokerGateway = brokerGateway;
        this.tradeOrderRepository = tradeOrderRepository;
        this.portfolioUpdateService = portfolioUpdateService;
        this.brokerAccountRepository = brokerAccountRepository;
        this.clock = clock;
    }

    @Transactional
    public List<TradeOrderEntity> execute(StrategyInstanceEntity instance, List<TradeOrderIntentEntity> intents) {
        if (instance.getBrokerAccount() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "live 인스턴스에 broker_account가 없습니다: " + instance.getId());
        }
        BrokerAccountEntity brokerAccount = brokerAccountRepository
                .findById(instance.getBrokerAccount().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "broker_account를 찾을 수 없습니다: " + instance.getBrokerAccount().getId()));
        List<TradeOrderEntity> orders = new ArrayList<>();
        for (TradeOrderIntentEntity intent : intents) {
            if (intent.getExecutionBlockedReason() != null) {
                continue;
            }
            orders.add(placeAndPersist(instance, brokerAccount, intent));
        }
        return orders;
    }

    private TradeOrderEntity placeAndPersist(
            StrategyInstanceEntity instance,
            BrokerAccountEntity brokerAccount,
            TradeOrderIntentEntity intent) {
        String clientOrderId = "live-" + UUID.randomUUID();
        BrokerOrderRequest request = new BrokerOrderRequest(
                brokerAccount,
                clientOrderId,
                intent.getSymbolCode(),
                intent.getSide(),
                intent.getQuantity(),
                intent.getOrderType(),
                intent.getPrice());
        log.info("live.order.place instanceId={} symbol={} side={} qty={} brokerAccount={} clientOrderId={}",
                instance.getId(), intent.getSymbolCode(), intent.getSide(), intent.getQuantity(),
                BrokerAccountMasker.mask(brokerAccount.getBrokerAccountNo()),
                clientOrderId);

        PlaceOrderResult result;
        try {
            result = brokerGateway.placeOrder(request);
        } catch (BrokerGatewayException ex) {
            return persistFailureOrder(instance, intent, clientOrderId, ex.getMessage());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        TradeOrderEntity order = new TradeOrderEntity();
        order.setTradeOrderIntent(intent);
        order.setStrategyInstance(instance);
        order.setClientOrderId(result.clientOrderId() != null ? result.clientOrderId() : clientOrderId);
        order.setBrokerOrderNo(result.brokerOrderNo());
        order.setExecutionMode(EXECUTION_MODE_LIVE);
        order.setOrderStatus(result.orderStatus());
        order.setRequestedQuantity(intent.getQuantity());
        order.setRequestedPrice("LIMIT".equals(intent.getOrderType()) ? intent.getPrice() : null);
        order.setFilledQuantity(result.filledQuantity() != null ? result.filledQuantity() : BigDecimal.ZERO);
        order.setAvgFilledPrice(result.avgFilledPrice());
        order.setRequestedAt(now);

        if (PlaceOrderResult.STATUS_REJECTED.equals(result.orderStatus())
                || PlaceOrderResult.STATUS_FAILED.equals(result.orderStatus())) {
            order.setFailedAt(now);
            order.setFailureReason(result.failureReason());
            order.setPortfolioAfterJson(snapshotJson(instance.getId()));
            return tradeOrderRepository.saveAndFlush(order);
        }

        order.setAcceptedAt(now);
        if (PlaceOrderResult.STATUS_FILLED.equals(result.orderStatus())) {
            order.setFilledAt(now);
        }

        BigDecimal deltaQty = order.getFilledQuantity();
        if (deltaQty != null && deltaQty.signum() > 0 && result.avgFilledPrice() != null) {
            PortfolioSnapshot snapshot = applyPortfolio(instance, intent, deltaQty, result.avgFilledPrice());
            order.setPortfolioAfterJson(portfolioUpdateService.toJson(snapshot));
        } else {
            order.setPortfolioAfterJson(snapshotJson(instance.getId()));
        }
        return tradeOrderRepository.saveAndFlush(order);
    }

    private TradeOrderEntity persistFailureOrder(
            StrategyInstanceEntity instance,
            TradeOrderIntentEntity intent,
            String clientOrderId,
            String failureReason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        TradeOrderEntity order = new TradeOrderEntity();
        order.setTradeOrderIntent(intent);
        order.setStrategyInstance(instance);
        order.setClientOrderId(clientOrderId);
        order.setExecutionMode(EXECUTION_MODE_LIVE);
        order.setOrderStatus(PlaceOrderResult.STATUS_FAILED);
        order.setRequestedQuantity(intent.getQuantity());
        order.setRequestedPrice("LIMIT".equals(intent.getOrderType()) ? intent.getPrice() : null);
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setRequestedAt(now);
        order.setFailedAt(now);
        order.setFailureReason(failureReason);
        order.setPortfolioAfterJson(snapshotJson(instance.getId()));
        return tradeOrderRepository.saveAndFlush(order);
    }

    private PortfolioSnapshot applyPortfolio(
            StrategyInstanceEntity instance,
            TradeOrderIntentEntity intent,
            BigDecimal qty,
            BigDecimal fillPrice) {
        if (SIDE_BUY.equals(intent.getSide())) {
            return portfolioUpdateService.applyBuy(instance.getId(), intent.getSymbolCode(), qty, fillPrice);
        }
        if (SIDE_SELL.equals(intent.getSide())) {
            return portfolioUpdateService.applySell(instance.getId(), intent.getSymbolCode(), qty, fillPrice);
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "지원하지 않는 side: " + intent.getSide());
    }

    private com.fasterxml.jackson.databind.JsonNode snapshotJson(UUID strategyInstanceId) {
        try {
            return portfolioUpdateService.toJson(portfolioUpdateService.snapshot(strategyInstanceId));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
