package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

import static org.assertj.core.api.Assertions.assertThat;

class PaperTradingCycleE2ETest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

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

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void executeDecisionFlowsThroughOrderAndPortfolio() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        addWatchlist(instance, samsung);
        seedPortfolio(instance, new BigDecimal("5000000.0000"));

        fakeTradingDecisionEngine.primeSuccess("""
                {
                  "cycleStatus":"EXECUTE",
                  "summary":"삼성전자 5주 매수",
                  "confidence":0.71,
                  "orders":[
                    {"sequenceNo":1,"symbolCode":"005930","side":"BUY","quantity":5,"orderType":"LIMIT","price":80000,"rationale":"실적 기대"}
                  ]
                }
                """);

        CycleResult result = orchestrator.runOnce(instance.getId(), "test-worker");

        assertThat(result.status()).isEqualTo(CycleResult.Status.COMPLETED);
        assertThat(result.cycleStatus()).isEqualTo("EXECUTE");

        TradeCycleLogEntity cycleLog = tradeCycleLogRepository.findById(result.cycleLogId()).orElseThrow();
        assertThat(cycleLog.getCycleStage()).isEqualTo(TradeCycleLifecycle.STAGE_COMPLETED);
        assertThat(cycleLog.getCycleFinishedAt()).isNotNull();

        List<TradeDecisionLogEntity> decisionLogs = tradeDecisionLogRepository.findAll();
        assertThat(decisionLogs).hasSize(1);
        TradeDecisionLogEntity decisionLog = decisionLogs.get(0);
        assertThat(decisionLog.getCycleStatus()).isEqualTo("EXECUTE");
        assertThat(decisionLog.getSummary()).isEqualTo("삼성전자 5주 매수");

        List<TradeOrderIntentEntity> intents = tradeOrderIntentRepository
                .findByTradeDecisionLog_IdOrderBySequenceNoAsc(decisionLog.getId());
        assertThat(intents).hasSize(1);
        TradeOrderIntentEntity intent = intents.get(0);
        assertThat(intent.getExecutionBlockedReason()).isNull();

        List<TradeOrderEntity> orders = tradeOrderRepository.findAll();
        assertThat(orders).hasSize(1);
        TradeOrderEntity order = orders.get(0);
        assertThat(order.getOrderStatus()).isEqualTo("filled");
        assertThat(order.getAvgFilledPrice()).isEqualByComparingTo("80000");
        assertThat(order.getTradeOrderIntent().getId()).isEqualTo(intent.getId());
        assertThat(order.getPortfolioAfterJson()).isNotNull();

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        // cash = 5,000,000 - 5*80000 = 4,600,000
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("4600000.0000");
        assertThat(portfolioPositionRepository.findByStrategyInstanceIdAndSymbolCode(instance.getId(), "005930")
                .orElseThrow()
                .getQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void holdDecisionCompletesWithoutOrders() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 B", "paper");

        // default fake = HOLD
        CycleResult result = orchestrator.runOnce(instance.getId(), "test-worker");

        assertThat(result.status()).isEqualTo(CycleResult.Status.COMPLETED);
        assertThat(result.cycleStatus()).isEqualTo("HOLD");
        assertThat(tradeOrderIntentRepository.findAll()).isEmpty();
        assertThat(tradeOrderRepository.findAll()).isEmpty();

        TradeDecisionLogEntity decisionLog = tradeDecisionLogRepository.findAll().get(0);
        assertThat(decisionLog.getCycleStatus()).isEqualTo("HOLD");
    }

    private void addWatchlist(StrategyInstanceEntity instance, AssetMasterEntity asset) {
        StrategyInstanceWatchlistRelationEntity relation = new StrategyInstanceWatchlistRelationEntity();
        relation.setStrategyInstance(instance);
        relation.setAssetMaster(asset);
        watchlistRelationRepository.saveAndFlush(relation);
    }

    private void seedPortfolio(StrategyInstanceEntity instance, BigDecimal cash) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(cash);
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);
    }
}
