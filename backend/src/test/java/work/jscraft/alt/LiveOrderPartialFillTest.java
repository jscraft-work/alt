package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.broker.OrderStatusResult;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.order.LiveOrderExecutor;
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

class LiveOrderPartialFillTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private LiveOrderExecutor liveOrderExecutor;

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

    @Autowired
    private PortfolioPositionRepository portfolioPositionRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeBrokerGateway.resetAll();
    }

    @Test
    void partialFillThenFullFillAccumulatesQuantityAndCashSpending() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE A");
        seedCash(instance, new BigDecimal("10000000.0000"));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, "005930", "BUY", "LIMIT",
                new BigDecimal("10"), new BigDecimal("80000"));

        // 최초 제출 시 3주만 체결
        fakeBrokerGateway.primePlaceResult(new PlaceOrderResult(
                "stub", "BROKER-P", PlaceOrderResult.STATUS_PARTIAL,
                new BigDecimal("3"), new BigDecimal("80000"), null));
        List<TradeOrderEntity> orders = liveOrderExecutor.execute(instance, List.of(intent));
        assertThat(orders).hasSize(1);
        TradeOrderEntity order = orders.get(0);
        assertThat(order.getOrderStatus()).isEqualTo(PlaceOrderResult.STATUS_PARTIAL);
        assertThat(order.getFilledQuantity()).isEqualByComparingTo("3");

        PortfolioEntity afterFirst = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        // cash = 10M - 3*80000 = 9,760,000
        assertThat(afterFirst.getCashAmount()).isEqualByComparingTo("9760000.0000");

        PortfolioPositionEntity afterFirstPosition = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(instance.getId(), "005930").orElseThrow();
        assertThat(afterFirstPosition.getQuantity()).isEqualByComparingTo("3");

        // 다음 사이클에서 reconcile 호출 → 7주 추가 체결 (총 10주)
        fakeBrokerGateway.primeStatus("BROKER-P", new OrderStatusResult(
                "BROKER-P", PlaceOrderResult.STATUS_FILLED,
                new BigDecimal("10"), new BigDecimal("80000"), null));
        ReconcileResult reconcileResult = reconcileService.reconcile(instance);
        assertThat(reconcileResult.success()).isTrue();
        assertThat(reconcileResult.updatedOrders()).isEqualTo(1);

        TradeOrderEntity reloaded = tradeOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getOrderStatus()).isEqualTo(PlaceOrderResult.STATUS_FILLED);
        assertThat(reloaded.getFilledQuantity()).isEqualByComparingTo("10");
        assertThat(reloaded.getFilledAt()).isNotNull();

        PortfolioEntity afterReconcile = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        // cash = 9,760,000 - 7 * 80000 = 9,200,000
        assertThat(afterReconcile.getCashAmount()).isEqualByComparingTo("9200000.0000");
        PortfolioPositionEntity afterReconcilePosition = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(instance.getId(), "005930").orElseThrow();
        assertThat(afterReconcilePosition.getQuantity()).isEqualByComparingTo("10");
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
        var cycleLog = tradeCycleLogRepository.findById(cycleId).orElseThrow();
        TradeDecisionLogEntity log = new TradeDecisionLogEntity();
        log.setTradeCycleLog(cycleLog);
        log.setStrategyInstance(instance);
        log.setCycleStartedAt(cycleLog.getCycleStartedAt());
        log.setCycleFinishedAt(cycleLog.getCycleStartedAt());
        log.setBusinessDate(cycleLog.getBusinessDate());
        log.setCycleStatus("EXECUTE");
        log.setSummary("partial");
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
