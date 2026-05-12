package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.broker.OrderStatusResult;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.reconcile.ReconcileService;
import work.jscraft.alt.trading.application.reconcile.ReconcileService.ReconcileResult;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcileWorkflowTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private ReconcileService reconcileService;

    @Autowired
    private TradeCycleLifecycle lifecycle;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @Autowired
    private TradeDecisionLogRepository tradeDecisionLogRepository;

    @Autowired
    private TradeOrderIntentRepository tradeOrderIntentRepository;

    @Autowired
    private TradeOrderRepository tradeOrderRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeBrokerGateway.resetAll();
    }

    @Test
    void noPendingOrdersIsSuccessfulNoOp() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE A");
        seedCash(instance, new BigDecimal("1000000.0000"));

        ReconcileResult result = reconcileService.reconcile(instance);

        assertThat(result.success()).isTrue();
        assertThat(result.updatedOrders()).isEqualTo(0);
    }

    @Test
    void pendingOrderGetsUpdatedFromBrokerStatus() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE B");
        seedCash(instance, new BigDecimal("10000000.0000"));
        TradeOrderEntity pending = seedPendingOrder(instance, "005930", "BUY",
                new BigDecimal("5"), new BigDecimal("81000"), "BROKER-1",
                PlaceOrderResult.STATUS_ACCEPTED, BigDecimal.ZERO);

        fakeBrokerGateway.primeStatus("BROKER-1", new OrderStatusResult(
                "BROKER-1", PlaceOrderResult.STATUS_FILLED,
                new BigDecimal("5"), new BigDecimal("81000"), null));

        ReconcileResult result = reconcileService.reconcile(instance);

        assertThat(result.success()).isTrue();
        assertThat(result.updatedOrders()).isEqualTo(1);

        TradeOrderEntity reloaded = tradeOrderRepository.findById(pending.getId()).orElseThrow();
        assertThat(reloaded.getOrderStatus()).isEqualTo(PlaceOrderResult.STATUS_FILLED);
        assertThat(reloaded.getFilledQuantity()).isEqualByComparingTo("5");
        assertThat(reloaded.getAvgFilledPrice()).isEqualByComparingTo("81000");
        assertThat(reloaded.getFilledAt()).isNotNull();

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        // cash = 10M - 5*81000 = 9,595,000
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("9595000.0000");
    }

    @Test
    void rejectedStatusMarksOrderRejected() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE C");
        seedCash(instance, new BigDecimal("1000000.0000"));
        TradeOrderEntity pending = seedPendingOrder(instance, "005930", "BUY",
                new BigDecimal("5"), new BigDecimal("81000"), "BROKER-R",
                PlaceOrderResult.STATUS_ACCEPTED, BigDecimal.ZERO);

        fakeBrokerGateway.primeStatus("BROKER-R", new OrderStatusResult(
                "BROKER-R", PlaceOrderResult.STATUS_REJECTED,
                BigDecimal.ZERO, null, "거래 정지"));

        ReconcileResult result = reconcileService.reconcile(instance);

        assertThat(result.success()).isTrue();
        TradeOrderEntity reloaded = tradeOrderRepository.findById(pending.getId()).orElseThrow();
        assertThat(reloaded.getOrderStatus()).isEqualTo(PlaceOrderResult.STATUS_REJECTED);
        assertThat(reloaded.getFailureReason()).isEqualTo("거래 정지");
        assertThat(reloaded.getFailedAt()).isNotNull();
    }

    private void seedCash(StrategyInstanceEntity instance, BigDecimal cash) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(cash);
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);
    }

    private TradeOrderEntity seedPendingOrder(
            StrategyInstanceEntity instance,
            String symbol,
            String side,
            BigDecimal qty,
            BigDecimal price,
            String brokerOrderNo,
            String status,
            BigDecimal filledQty) {
        UUID cycleId = lifecycle.startCycle(instance);
        var cycleLog = tradeCycleLogRepository.findById(cycleId).orElseThrow();
        TradeDecisionLogEntity decisionLog = new TradeDecisionLogEntity();
        decisionLog.setTradeCycleLog(cycleLog);
        decisionLog.setStrategyInstance(instance);
        decisionLog.setCycleStartedAt(cycleLog.getCycleStartedAt());
        decisionLog.setCycleFinishedAt(cycleLog.getCycleStartedAt());
        decisionLog.setBusinessDate(cycleLog.getBusinessDate());
        decisionLog.setCycleStatus("EXECUTE");
        decisionLog.setSummary("reconcile target");
        decisionLog.setSettingsSnapshotJson(objectMapper.createObjectNode());
        decisionLog = tradeDecisionLogRepository.saveAndFlush(decisionLog);

        TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
        intent.setTradeDecisionLog(decisionLog);
        intent.setSequenceNo(1);
        intent.setSymbolCode(symbol);
        intent.setSide(side);
        intent.setOrderType("LIMIT");
        intent.setQuantity(qty);
        intent.setPrice(price);
        intent.setRationale("seed");
        intent.setEvidenceJson(objectMapper.createArrayNode());
        intent = tradeOrderIntentRepository.saveAndFlush(intent);

        TradeOrderEntity order = new TradeOrderEntity();
        order.setTradeOrderIntent(intent);
        order.setStrategyInstance(instance);
        order.setClientOrderId("live-" + UUID.randomUUID());
        order.setBrokerOrderNo(brokerOrderNo);
        order.setExecutionMode("live");
        order.setOrderStatus(status);
        order.setRequestedQuantity(qty);
        order.setRequestedPrice(price);
        order.setFilledQuantity(filledQty);
        order.setRequestedAt(OffsetDateTime.now(mutableClock));
        order.setAcceptedAt(OffsetDateTime.now(mutableClock));
        return tradeOrderRepository.saveAndFlush(order);
    }
}
