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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
import work.jscraft.alt.trading.domain.TradeOrderStatus;
import work.jscraft.alt.trading.application.ops.TradingIncidentReporter;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

@Service
public class LiveOrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(LiveOrderExecutor.class);

    public static final String EXECUTION_MODE_LIVE = "live";
    public static final String SIDE_BUY = "BUY";
    public static final String SIDE_SELL = "SELL";
    public static final String ORDER_STATUS_REQUESTED = TradeOrderStatus.REQUESTED.wireValue();
    public static final String ORDER_STATUS_SUBMISSION_UNKNOWN = TradeOrderStatus.SUBMISSION_UNKNOWN.wireValue();

    private final BrokerGateway brokerGateway;
    private final TradeOrderRepository tradeOrderRepository;
    private final PortfolioUpdateService portfolioUpdateService;
    private final BrokerAccountRepository brokerAccountRepository;
    private final TradingIncidentReporter incidentReporter;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final Clock clock;

    public LiveOrderExecutor(
            BrokerGateway brokerGateway,
            TradeOrderRepository tradeOrderRepository,
            PortfolioUpdateService portfolioUpdateService,
            BrokerAccountRepository brokerAccountRepository,
            TradingIncidentReporter incidentReporter,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.brokerGateway = brokerGateway;
        this.tradeOrderRepository = tradeOrderRepository;
        this.portfolioUpdateService = portfolioUpdateService;
        this.brokerAccountRepository = brokerAccountRepository;
        this.incidentReporter = incidentReporter;
        this.requiresNewTransactionTemplate = requiresNewTemplate(transactionManager);
        this.clock = clock;
    }

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
        TradeOrderEntity pendingOrder = createRequestedOrder(instance, intent, clientOrderId);
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
            TradeOrderEntity persisted = isSubmissionUnknown(ex)
                    ? markSubmissionUnknown(pendingOrder.getId(), ex)
                    : markSubmissionFailed(pendingOrder.getId(), ex.getMessage());
            if (isSubmissionUnknown(ex)) {
                incidentReporter.reportLiveOrderSubmissionUnknown(
                        instance, intent, clientOrderId, formatFailureReason(ex));
            } else {
                incidentReporter.reportLiveOrderFailure(
                        instance, intent, clientOrderId, formatFailureReason(ex));
            }
            return persisted;
        }

        TradeOrderEntity persisted = markSubmissionResult(pendingOrder.getId(), result);
        if (PlaceOrderResult.STATUS_REJECTED.equals(result.orderStatus())
                || PlaceOrderResult.STATUS_FAILED.equals(result.orderStatus())) {
            incidentReporter.reportLiveOrderFailure(
                    instance, intent, persisted.getClientOrderId(),
                    result.orderStatus() + ": " + result.failureReason());
        }
        return persisted;
    }

    private TradeOrderEntity createRequestedOrder(
            StrategyInstanceEntity instance,
            TradeOrderIntentEntity intent,
            String clientOrderId) {
        return requiresNewTransactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(clock);
            TradeOrderEntity order = new TradeOrderEntity();
            order.setTradeOrderIntent(intent);
            order.setStrategyInstance(instance);
            order.setClientOrderId(clientOrderId);
            order.setExecutionMode(EXECUTION_MODE_LIVE);
            order.setOrderStatus(ORDER_STATUS_REQUESTED);
            order.setRequestedQuantity(intent.getQuantity());
            order.setRequestedPrice("LIMIT".equals(intent.getOrderType()) ? intent.getPrice() : null);
            order.setFilledQuantity(BigDecimal.ZERO);
            order.setRequestedAt(now);
            return tradeOrderRepository.saveAndFlush(order);
        });
    }

    private TradeOrderEntity markSubmissionResult(UUID tradeOrderId, PlaceOrderResult result) {
        return requiresNewTransactionTemplate.execute(status -> {
            TradeOrderEntity order = requireTradeOrder(tradeOrderId);
            StrategyInstanceEntity instance = order.getStrategyInstance();
            TradeOrderIntentEntity intent = order.getTradeOrderIntent();
            OffsetDateTime now = OffsetDateTime.now(clock);
            order.setClientOrderId(result.clientOrderId() != null ? result.clientOrderId() : order.getClientOrderId());
            order.setBrokerOrderNo(result.brokerOrderNo());
            order.setOrderStatus(result.orderStatus());
            order.setFilledQuantity(result.filledQuantity() != null ? result.filledQuantity() : BigDecimal.ZERO);
            order.setAvgFilledPrice(result.avgFilledPrice());
            order.setFailureReason(result.failureReason());

            TradeOrderStatus resultStatus = TradeOrderStatus.fromWire(result.orderStatus());
            if (resultStatus.isTerminalFailure()) {
                order.setFailedAt(now);
                order.setPortfolioAfterJson(snapshotJson(instance.getId()));
                return tradeOrderRepository.saveAndFlush(order);
            }

            order.setAcceptedAt(now);
            if (TradeOrderStatus.FILLED == resultStatus) {
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
        });
    }

    private TradeOrderEntity markSubmissionFailed(UUID tradeOrderId, String failureReason) {
        return requiresNewTransactionTemplate.execute(status -> {
            TradeOrderEntity order = requireTradeOrder(tradeOrderId);
            order.setOrderStatus(TradeOrderStatus.FAILED.wireValue());
            order.setFailedAt(OffsetDateTime.now(clock));
            order.setFailureReason(failureReason);
            order.setPortfolioAfterJson(snapshotJson(order.getStrategyInstance().getId()));
            return tradeOrderRepository.saveAndFlush(order);
        });
    }

    private TradeOrderEntity markSubmissionUnknown(UUID tradeOrderId, BrokerGatewayException exception) {
        return requiresNewTransactionTemplate.execute(status -> {
            TradeOrderEntity order = requireTradeOrder(tradeOrderId);
            order.setOrderStatus(TradeOrderStatus.SUBMISSION_UNKNOWN.wireValue());
            order.setFailureReason(formatFailureReason(exception));
            order.setPortfolioAfterJson(snapshotJson(order.getStrategyInstance().getId()));
            return tradeOrderRepository.saveAndFlush(order);
        });
    }

    private TradeOrderEntity requireTradeOrder(UUID tradeOrderId) {
        return tradeOrderRepository.findById(tradeOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "trade_order를 찾을 수 없습니다: " + tradeOrderId));
    }

    private boolean isSubmissionUnknown(BrokerGatewayException exception) {
        return switch (exception.getCategory()) {
            case TRANSIENT, TIMEOUT, INVALID_RESPONSE, EMPTY_RESPONSE -> true;
            case AUTH_FAILED, REQUEST_INVALID, ORDER_NOT_FOUND -> false;
        };
    }

    private String formatFailureReason(BrokerGatewayException exception) {
        return exception.getCategory().name() + ": " + exception.getMessage();
    }

    private TransactionTemplate requiresNewTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
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
