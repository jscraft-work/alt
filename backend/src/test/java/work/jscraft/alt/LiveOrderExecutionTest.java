package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.order.LiveOrderExecutor;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;

import static org.assertj.core.api.Assertions.assertThat;

class LiveOrderExecutionTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private LiveOrderExecutor liveOrderExecutor;

    @Autowired
    private TradeCycleLifecycle lifecycle;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @Autowired
    private TradeDecisionLogRepository tradeDecisionLogRepository;

    @Autowired
    private TradeOrderIntentRepository tradeOrderIntentRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeBrokerGateway.resetAll();
    }

    @Test
    void brokerAcceptanceCreatesAcceptedTradeOrder() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE A");
        seedCash(instance, new BigDecimal("10000000.0000"));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, "005930", "BUY", "LIMIT",
                new BigDecimal("5"), new BigDecimal("81000"));

        fakeBrokerGateway.primePlaceResult(new PlaceOrderResult(
                "client-stub", "BROKER-001", PlaceOrderResult.STATUS_ACCEPTED,
                BigDecimal.ZERO, null, null));

        List<TradeOrderEntity> orders = liveOrderExecutor.execute(instance, List.of(intent));

        assertThat(orders).hasSize(1);
        TradeOrderEntity order = orders.get(0);
        assertThat(order.getExecutionMode()).isEqualTo("live");
        assertThat(order.getOrderStatus()).isEqualTo(PlaceOrderResult.STATUS_ACCEPTED);
        assertThat(order.getBrokerOrderNo()).isEqualTo("BROKER-001");
        assertThat(order.getFilledQuantity()).isEqualByComparingTo("0");
        assertThat(order.getAcceptedAt()).isNotNull();
        assertThat(order.getFilledAt()).isNull();
        assertThat(order.getRequestedPrice()).isEqualByComparingTo("81000");

        // broker로 전달된 요청 자체는 1건이며 LIMIT/BUY/quantity가 그대로 전달된다
        assertThat(fakeBrokerGateway.placeOrderInvocations()).hasSize(1);
        var sent = fakeBrokerGateway.placeOrderInvocations().get(0);
        assertThat(sent.symbolCode()).isEqualTo("005930");
        assertThat(sent.side()).isEqualTo("BUY");
        assertThat(sent.orderType()).isEqualTo("LIMIT");
        assertThat(sent.quantity()).isEqualByComparingTo("5");
        assertThat(sent.price()).isEqualByComparingTo("81000");

        // 매수가 accept일 뿐 fill되지 않았으므로 현금 변화 없음
        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("10000000.0000");
    }

    @Test
    void brokerRejectionMarksOrderRejectedAndDoesNotMovePortfolio() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE B");
        seedCash(instance, new BigDecimal("100000.0000"));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, "005930", "BUY", "LIMIT",
                new BigDecimal("10"), new BigDecimal("80000"));

        fakeBrokerGateway.primePlaceResult(new PlaceOrderResult(
                "client-stub", null, PlaceOrderResult.STATUS_REJECTED,
                BigDecimal.ZERO, null, "잔고 부족"));

        List<TradeOrderEntity> orders = liveOrderExecutor.execute(instance, List.of(intent));

        TradeOrderEntity order = orders.get(0);
        assertThat(order.getOrderStatus()).isEqualTo(PlaceOrderResult.STATUS_REJECTED);
        assertThat(order.getFailureReason()).isEqualTo("잔고 부족");
        assertThat(order.getFailedAt()).isNotNull();
        assertThat(order.getBrokerOrderNo()).isNull();

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("100000.0000");
    }

    @Test
    void immediateFillUpdatesPortfolioAndOrderState() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE C");
        seedCash(instance, new BigDecimal("10000000.0000"));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, "005930", "BUY", "LIMIT",
                new BigDecimal("5"), new BigDecimal("81000"));

        fakeBrokerGateway.primePlaceResult(new PlaceOrderResult(
                "client-stub", "BROKER-FILL", PlaceOrderResult.STATUS_FILLED,
                new BigDecimal("5"), new BigDecimal("81000"), null));

        List<TradeOrderEntity> orders = liveOrderExecutor.execute(instance, List.of(intent));

        TradeOrderEntity order = orders.get(0);
        assertThat(order.getOrderStatus()).isEqualTo(PlaceOrderResult.STATUS_FILLED);
        assertThat(order.getFilledQuantity()).isEqualByComparingTo("5");
        assertThat(order.getAvgFilledPrice()).isEqualByComparingTo("81000");
        assertThat(order.getFilledAt()).isNotNull();

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("9595000.0000");
        assertThat(order.getPortfolioAfterJson()).isNotNull();
    }

    @Test
    void timeoutLeavesSubmissionUnknownRecordForManualReconcile() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE D");
        seedCash(instance, new BigDecimal("10000000.0000"));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, "005930", "BUY", "LIMIT",
                new BigDecimal("5"), new BigDecimal("81000"));

        fakeBrokerGateway.primePlaceFailure(new BrokerGatewayException(
                BrokerGatewayException.Category.TIMEOUT, "fake", "broker timeout"));

        List<TradeOrderEntity> orders = liveOrderExecutor.execute(instance, List.of(intent));

        assertThat(orders).hasSize(1);
        TradeOrderEntity order = orders.get(0);
        assertThat(order.getOrderStatus()).isEqualTo(LiveOrderExecutor.ORDER_STATUS_SUBMISSION_UNKNOWN);
        assertThat(order.getBrokerOrderNo()).isNull();
        assertThat(order.getFailureReason()).contains("TIMEOUT");

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("10000000.0000");
    }

    private void seedCash(StrategyInstanceEntity instance, BigDecimal cash) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(cash);
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);
    }

    private TradeDecisionLogEntity seedDecisionLog(StrategyInstanceEntity instance) {
        UUID cycleId = lifecycle.startCycle(instance);
        TradeCycleLogEntity cycleLog = tradeCycleLogRepository.findById(cycleId).orElseThrow();
        TradeDecisionLogEntity log = new TradeDecisionLogEntity();
        log.setTradeCycleLog(cycleLog);
        log.setStrategyInstance(instance);
        log.setCycleStartedAt(cycleLog.getCycleStartedAt());
        log.setCycleFinishedAt(cycleLog.getCycleStartedAt());
        log.setBusinessDate(cycleLog.getBusinessDate());
        log.setCycleStatus("EXECUTE");
        log.setSummary("test");
        log.setSettingsSnapshotJson(objectMapper.createObjectNode());
        return tradeDecisionLogRepository.saveAndFlush(log);
    }

    private TradeOrderIntentEntity seedIntent(
            TradeDecisionLogEntity decisionLog,
            String symbol,
            String side,
            String orderType,
            BigDecimal qty,
            BigDecimal price) {
        TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
        intent.setTradeDecisionLog(decisionLog);
        intent.setSequenceNo(1);
        intent.setSymbolCode(symbol);
        intent.setSymbolName("Test Stock");
        intent.setSide(side);
        intent.setOrderType(orderType);
        intent.setQuantity(qty);
        intent.setPrice(price);
        intent.setRationale("test");
        intent.setEvidenceJson(objectMapper.createArrayNode());
        return tradeOrderIntentRepository.saveAndFlush(intent);
    }
}
