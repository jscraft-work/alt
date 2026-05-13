package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.marketdata.KisPriceCollector;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FailurePathRegressionTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private KisPriceCollector kisPriceCollector;

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

    @Autowired
    private TradeCycleLifecycle lifecycle;

    @Autowired
    private OpsEventRepository opsEventRepository;

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
        fakeMarketDataGateway.resetAll();
        fakeBrokerGateway.resetAll();
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void loginFailureReturns401AndDoesNotLeakInternalDetails() throws Exception {
        createAdminUser();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LoginRequest("admin", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").exists());
    }

    @Test
    void collectorFailureRecordsDownOpsEvent() {
        fakeMarketDataGateway.primePriceFailure("005930",
                new MarketDataException(MarketDataException.Category.TIMEOUT, "kis", "수집 타임아웃"));

        try {
            kisPriceCollector.collect("005930");
        } catch (MarketDataException expected) {
            // 의도된 실패
        }

        List<OpsEventEntity> events = opsEventRepository.findAll();
        assertThat(events).hasSize(1);
        OpsEventEntity event = events.get(0);
        assertThat(event.getServiceName()).isEqualTo(MarketDataOpsEventRecorder.SERVICE_MARKETDATA);
        assertThat(event.getStatusCode()).isEqualTo(MarketDataOpsEventRecorder.STATUS_DOWN);
        assertThat(event.getPayloadJson().path("category").asText()).isEqualTo("TIMEOUT");
    }

    @Test
    void llmTimeoutMarksCycleAndDecisionLogFailed() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 LLM-FAIL", "paper");
        fakeTradingDecisionEngine.primeResult(LlmCallResult.timeout("subprocess 30s timeout"));

        CycleResult result = orchestrator.runOnce(instance.getId(), "regress-worker");

        assertThat(result.status()).isEqualTo(CycleResult.Status.FAILED);

        TradeCycleLogEntity cycleLog = tradeCycleLogRepository.findById(result.cycleLogId()).orElseThrow();
        assertThat(cycleLog.getCycleStage()).isEqualTo(TradeCycleLifecycle.STAGE_FAILED);
        assertThat(cycleLog.getFailureReason()).isEqualTo("LLM_TIMEOUT");

        List<TradeDecisionLogEntity> decisionLogs = tradeDecisionLogRepository.findAll();
        assertThat(decisionLogs).hasSize(1);
        assertThat(decisionLogs.get(0).getCycleStatus()).isEqualTo("FAILED");
        assertThat(decisionLogs.get(0).getCallStatus()).isEqualTo("TIMEOUT");
    }

    @Test
    void reconcileFailureAutoPausesLiveInstance() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE RECON-FAIL");
        seedPortfolio(instance);
        seedPendingLiveOrder(instance, "BROKER-X");

        fakeBrokerGateway.primeStatusFailure("BROKER-X",
                new BrokerGatewayException(BrokerGatewayException.Category.TIMEOUT, "kis", "broker timeout"));

        CycleResult result = orchestrator.runOnce(instance.getId(), "regress-worker");

        assertThat(result.status()).isEqualTo(CycleResult.Status.FAILED);
        TradeCycleLogEntity cycleLog = tradeCycleLogRepository.findById(result.cycleLogId()).orElseThrow();
        assertThat(cycleLog.getAutoPausedReason()).isEqualTo(ReconcileService.AUTO_PAUSED_REASON);

        StrategyInstanceEntity reloaded = strategyInstanceRepository.findById(instance.getId()).orElseThrow();
        assertThat(reloaded.getAutoPausedReason()).isEqualTo(ReconcileService.AUTO_PAUSED_REASON);

        // 다음 호출은 SKIPPED(AUTO_PAUSED)
        CycleResult retry = orchestrator.runOnce(instance.getId(), "regress-worker");
        assertThat(retry.status()).isEqualTo(CycleResult.Status.SKIPPED);
        assertThat(retry.skipReason()).isEqualTo(CycleExecutionOrchestrator.SkipReason.AUTO_PAUSED);
    }

    private void seedPortfolio(StrategyInstanceEntity instance) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(new BigDecimal("1000000.0000"));
        portfolio.setTotalAssetAmount(new BigDecimal("1000000.0000"));
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
        intent.setSymbolName("Test Stock");
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
