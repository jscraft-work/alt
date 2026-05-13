package work.jscraft.alt.trading.application.reconcile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.portfolio.application.PortfolioUpdateService;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.trading.application.broker.BrokerGateway;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException;
import work.jscraft.alt.trading.application.broker.OrderStatusResult;
import work.jscraft.alt.trading.domain.TradeOrderStatus;
import work.jscraft.alt.trading.application.ops.TradingIncidentReporter;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

@Service
public class ReconcileService {

    public static final String AUTO_PAUSED_REASON = "reconcile_failed";
    public static final String EXECUTION_MODE_LIVE = "live";
    private static final List<String> PENDING_STATUSES = TradeOrderStatus.pendingWireValues();

    private static final Logger log = LoggerFactory.getLogger(ReconcileService.class);

    private final BrokerGateway brokerGateway;
    private final TradeOrderRepository tradeOrderRepository;
    private final StrategyInstanceRepository strategyInstanceRepository;
    private final PortfolioUpdateService portfolioUpdateService;
    private final TradingIncidentReporter incidentReporter;
    private final Clock clock;

    public ReconcileService(
            BrokerGateway brokerGateway,
            TradeOrderRepository tradeOrderRepository,
            StrategyInstanceRepository strategyInstanceRepository,
            PortfolioUpdateService portfolioUpdateService,
            TradingIncidentReporter incidentReporter,
            Clock clock) {
        this.brokerGateway = brokerGateway;
        this.tradeOrderRepository = tradeOrderRepository;
        this.strategyInstanceRepository = strategyInstanceRepository;
        this.portfolioUpdateService = portfolioUpdateService;
        this.incidentReporter = incidentReporter;
        this.clock = clock;
    }

    @Transactional
    public ReconcileResult reconcile(StrategyInstanceEntity instance) {
        if (instance.getBrokerAccount() == null) {
            return ReconcileResult.failure("broker_account 누락");
        }
        List<TradeOrderEntity> pending = tradeOrderRepository
                .findByStrategyInstance_IdAndExecutionModeAndOrderStatusIn(
                        instance.getId(), EXECUTION_MODE_LIVE, PENDING_STATUSES);
        int updated = 0;
        for (TradeOrderEntity order : pending) {
            if (order.getBrokerOrderNo() == null || order.getBrokerOrderNo().isBlank()) {
                return ReconcileResult.failure("broker_order_no가 없는 제출 미확정 주문: orderId="
                        + order.getId() + ", clientOrderId=" + order.getClientOrderId()
                        + ", status=" + order.getOrderStatus());
            }
            OrderStatusResult statusResult;
            try {
                statusResult = brokerGateway.getOrderStatus(instance.getBrokerAccount(), order.getBrokerOrderNo());
            } catch (BrokerGatewayException ex) {
                log.warn("reconcile 실패 instance={} broker_order_no={} category={} msg={}",
                        instance.getId(), order.getBrokerOrderNo(), ex.getCategory(), ex.getMessage());
                return ReconcileResult.failure(ex.getMessage());
            }
            applyStatusUpdate(instance, order, statusResult);
            updated++;
        }
        return ReconcileResult.success(updated);
    }

    @Transactional
    public void markAutoPaused(UUID strategyInstanceId, String reason) {
        StrategyInstanceEntity instance = strategyInstanceRepository.findById(strategyInstanceId)
                .orElseThrow();
        instance.setAutoPausedReason(reason);
        instance.setAutoPausedAt(OffsetDateTime.now(clock));
        strategyInstanceRepository.saveAndFlush(instance);
        incidentReporter.reportAutoPaused(instance, reason);
    }

    private void applyStatusUpdate(
            StrategyInstanceEntity instance,
            TradeOrderEntity order,
            OrderStatusResult statusResult) {
        BigDecimal previousFilled = order.getFilledQuantity() != null ? order.getFilledQuantity() : BigDecimal.ZERO;
        BigDecimal newFilled = statusResult.filledQuantity() != null
                ? statusResult.filledQuantity()
                : BigDecimal.ZERO;
        BigDecimal deltaQty = newFilled.subtract(previousFilled);

        order.setOrderStatus(statusResult.orderStatus());
        order.setFilledQuantity(newFilled);
        order.setAvgFilledPrice(statusResult.avgFilledPrice());
        OffsetDateTime now = OffsetDateTime.now(clock);
        TradeOrderStatus status = TradeOrderStatus.fromWire(statusResult.orderStatus());
        if (TradeOrderStatus.FILLED == status) {
            order.setFilledAt(now);
        }
        if (status.isTerminalFailure()) {
            order.setFailedAt(now);
            order.setFailureReason(statusResult.failureReason());
        }

        if (deltaQty.signum() > 0 && statusResult.avgFilledPrice() != null) {
            String side = order.getTradeOrderIntent().getSide();
            if (LiveOrderSide.BUY.matches(side)) {
                var snapshot = portfolioUpdateService.applyBuy(
                        instance.getId(), order.getTradeOrderIntent().getSymbolCode(),
                        deltaQty, statusResult.avgFilledPrice());
                order.setPortfolioAfterJson(portfolioUpdateService.toJson(snapshot));
            } else if (LiveOrderSide.SELL.matches(side)) {
                var snapshot = portfolioUpdateService.applySell(
                        instance.getId(), order.getTradeOrderIntent().getSymbolCode(),
                        deltaQty, statusResult.avgFilledPrice());
                order.setPortfolioAfterJson(portfolioUpdateService.toJson(snapshot));
            }
        }
        tradeOrderRepository.saveAndFlush(order);
    }

    public record ReconcileResult(boolean success, int updatedOrders, String failureMessage) {

        public static ReconcileResult success(int updatedOrders) {
            return new ReconcileResult(true, updatedOrders, null);
        }

        public static ReconcileResult failure(String message) {
            return new ReconcileResult(false, 0, message);
        }
    }

    private enum LiveOrderSide {
        BUY, SELL;

        boolean matches(String side) {
            return name().equals(side);
        }
    }
}
