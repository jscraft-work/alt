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
import work.jscraft.alt.trading.application.broker.BrokerGatewayException;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.reconcile.ReconcileService;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcileFailureAutoPauseTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

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
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void reconcileFailureMarksInstanceAutoPausedAndFailsCycle() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE A");
        seedCash(instance, new BigDecimal("1000000.0000"));
        // 미확정 live 주문이 존재 → reconcile은 broker.getOrderStatus 호출
        seedPendingLiveOrder(instance, "BROKER-X");
        fakeBrokerGateway.primeStatusFailure("BROKER-X",
                new BrokerGatewayException(BrokerGatewayException.Category.TIMEOUT, "kis", "broker timeout"));

        CycleResult result = orchestrator.runOnce(instance.getId(), "test-worker");

        assertThat(result.status()).isEqualTo(CycleResult.Status.FAILED);

        TradeCycleLogEntity cycleLog = tradeCycleLogRepository.findById(result.cycleLogId()).orElseThrow();
        assertThat(cycleLog.getCycleStage()).isEqualTo(TradeCycleLifecycle.STAGE_FAILED);
        assertThat(cycleLog.getFailureReason()).isEqualTo("RECONCILE_FAILED");
        assertThat(cycleLog.getAutoPausedReason()).isEqualTo(ReconcileService.AUTO_PAUSED_REASON);
        assertThat(cycleLog.getCycleFinishedAt()).isNotNull();

        StrategyInstanceEntity reloaded = strategyInstanceRepository.findById(instance.getId()).orElseThrow();
        assertThat(reloaded.getAutoPausedReason()).isEqualTo(ReconcileService.AUTO_PAUSED_REASON);
        assertThat(reloaded.getAutoPausedAt()).isNotNull();
    }

    @Test
    void autoPausedInstanceIsSkippedOnNextCycle() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE B");
        seedCash(instance, new BigDecimal("1000000.0000"));
        seedPendingLiveOrder(instance, "BROKER-Y");
        fakeBrokerGateway.primeStatusFailure("BROKER-Y",
                new BrokerGatewayException(BrokerGatewayException.Category.TIMEOUT, "kis", "timeout"));

        CycleResult firstResult = orchestrator.runOnce(instance.getId(), "test-worker");
        assertThat(firstResult.status()).isEqualTo(CycleResult.Status.FAILED);

        CycleResult retry = orchestrator.runOnce(instance.getId(), "test-worker");
        assertThat(retry.status()).isEqualTo(CycleResult.Status.SKIPPED);
        assertThat(retry.skipReason()).isEqualTo(CycleExecutionOrchestrator.SkipReason.AUTO_PAUSED);
    }

    private void seedCash(StrategyInstanceEntity instance, BigDecimal cash) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(cash);
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);
    }

    private void seedPendingLiveOrder(StrategyInstanceEntity instance, String brokerOrderNo) {
        UUID cycleId = lifecycle.startCycle(instance);
        var cycleLog = tradeCycleLogRepository.findById(cycleId).orElseThrow();
        TradeDecisionLogEntity decisionLog = new TradeDecisionLogEntity();
        decisionLog.setTradeCycleLog(cycleLog);
        decisionLog.setStrategyInstance(instance);
        decisionLog.setCycleStartedAt(cycleLog.getCycleStartedAt());
        decisionLog.setCycleFinishedAt(cycleLog.getCycleStartedAt());
        decisionLog.setBusinessDate(cycleLog.getBusinessDate());
        decisionLog.setCycleStatus("EXECUTE");
        decisionLog.setSummary("seed");
        decisionLog.setSettingsSnapshotJson(objectMapper.createObjectNode());
        decisionLog = tradeDecisionLogRepository.saveAndFlush(decisionLog);

        TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
        intent.setTradeDecisionLog(decisionLog);
        intent.setSequenceNo(1);
        intent.setSymbolCode("005930");
        intent.setSide("BUY");
        intent.setOrderType("LIMIT");
        intent.setQuantity(new BigDecimal("5"));
        intent.setPrice(new BigDecimal("80000"));
        intent.setRationale("seed");
        intent.setEvidenceJson(objectMapper.createArrayNode());
        intent = tradeOrderIntentRepository.saveAndFlush(intent);

        TradeOrderEntity order = new TradeOrderEntity();
        order.setTradeOrderIntent(intent);
        order.setStrategyInstance(instance);
        order.setClientOrderId("live-" + UUID.randomUUID());
        order.setBrokerOrderNo(brokerOrderNo);
        order.setExecutionMode("live");
        order.setOrderStatus(PlaceOrderResult.STATUS_ACCEPTED);
        order.setRequestedQuantity(new BigDecimal("5"));
        order.setRequestedPrice(new BigDecimal("80000"));
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setRequestedAt(OffsetDateTime.now(mutableClock));
        order.setAcceptedAt(OffsetDateTime.now(mutableClock));
        tradeOrderRepository.saveAndFlush(order);
    }
}
