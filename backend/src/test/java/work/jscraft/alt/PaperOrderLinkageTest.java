package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

import static org.assertj.core.api.Assertions.assertThat;

class PaperOrderLinkageTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

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
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void decisionLogIntentAndOrderAreFullyLinked() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        seedPortfolio(instance, new BigDecimal("3000000.0000"));

        fakeTradingDecisionEngine.primeSuccess("""
                {
                  "cycleStatus":"EXECUTE",
                  "summary":"매수 2건",
                  "orders":[
                    {"sequenceNo":1,"symbolCode":"005930","side":"BUY","quantity":2,"orderType":"LIMIT","price":81000,"rationale":"근거1"},
                    {"sequenceNo":2,"symbolCode":"005930","side":"BUY","quantity":3,"orderType":"LIMIT","price":80000,"rationale":"근거2"}
                  ]
                }
                """);

        CycleResult result = orchestrator.runOnce(instance.getId(), "test-worker");
        assertThat(result.status()).isEqualTo(CycleResult.Status.COMPLETED);
        assertThat(result.cycleLogId()).isNotNull();
        assertThat(result.decisionLogId()).isNotNull();

        java.util.UUID decisionLogId = result.decisionLogId();

        List<TradeOrderIntentEntity> intents = tradeOrderIntentRepository
                .findByTradeDecisionLog_IdOrderBySequenceNoAsc(decisionLogId);
        assertThat(intents).hasSize(2);
        java.util.Set<java.util.UUID> intentIds = intents.stream()
                .map(TradeOrderIntentEntity::getId)
                .collect(java.util.stream.Collectors.toSet());

        // decision_log → orders 역추적: 같은 decisionLog ID로 join한 결과가 2건이어야 한다
        List<TradeOrderEntity> ordersByDecision = tradeOrderRepository
                .findByTradeOrderIntent_TradeDecisionLog_IdOrderByRequestedAtAsc(decisionLogId);
        assertThat(ordersByDecision).hasSize(2);
        for (TradeOrderEntity order : ordersByDecision) {
            assertThat(intentIds).contains(order.getTradeOrderIntent().getId());
        }

        // paper에서는 intent 1건당 order 1건
        assertThat(ordersByDecision.stream().map(o -> o.getTradeOrderIntent().getId()).distinct().count())
                .isEqualTo(2L);

        // strategy_instance 연결 검증 (별도 repository 조회로 lazy 회피)
        List<TradeOrderEntity> ordersByInstance = tradeOrderRepository
                .findByStrategyInstance_IdOrderByRequestedAtDesc(
                        instance.getId(),
                        org.springframework.data.domain.Limit.of(10));
        assertThat(ordersByInstance).hasSize(2);
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
