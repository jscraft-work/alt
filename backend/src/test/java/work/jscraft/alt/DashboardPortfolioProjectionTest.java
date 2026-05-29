package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardPortfolioProjectionTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioPositionRepository portfolioPositionRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void instanceDashboardReflectsUpdatedPortfolioAfterPaperCycle() throws Exception {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        linkWatchlist(instance, samsung);
        seedPortfolio(instance, new BigDecimal("5000000.0000"));
        // walker 호가 시드 — M1 PaperOrderExecutor 가 walk 시뮬에 사용
        seedOrderbook("005930",
                java.util.List.of(80_000L, 80_100L, 80_200L, 80_300L, 80_400L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L),
                java.util.List.of(79_900L, 79_800L, 79_700L, 79_600L, 79_500L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L));

        fakeTradingDecisionEngine.primeSuccess("""
                {
                  "cycleStatus":"EXECUTE",
                  "summary":"삼성전자 5주 매수",
                  "orders":[
                    {"sequenceNo":1,"symbolCode":"005930","side":"BUY","quantity":5,"orderType":"LIMIT","price":80000,"rationale":"실적 기대"}
                  ]
                }
                """);

        CycleResult result = orchestrator.runOnce(instance.getId(), "test-worker");
        org.assertj.core.api.Assertions.assertThat(result.status())
                .isEqualTo(CycleResult.Status.COMPLETED);

        // 대시보드 인스턴스 조회: 갱신된 portfolio가 응답에 반영되어야 한다.
        // M1: walker avgFilled 80,100 (한 틱 양보) + commission → paperActual 400,556.07 → cash 4,599,443.93
        // total = cash + positions*avg = 4,599,443.93 + 5*80,100 = 4,999,943.93
        mockMvc.perform(get("/api/dashboard/instances/{id}", instance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolio.cashAmount").value(4599443.9300))
                .andExpect(jsonPath("$.data.portfolio.totalAssetAmount").value(4999943.9300))
                .andExpect(jsonPath("$.data.portfolio.realizedPnlToday").value(0))
                .andExpect(jsonPath("$.data.positions.length()").value(1))
                .andExpect(jsonPath("$.data.positions[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data.positions[0].quantity").value(5.00000000))
                .andExpect(jsonPath("$.data.positions[0].avgBuyPrice").value(80100.00000000))
                .andExpect(jsonPath("$.data.recentOrders.length()").value(1))
                .andExpect(jsonPath("$.data.recentOrders[0].orderStatus").value("filled"));

        // strategy-overview 카드도 같은 portfolio 값을 반영한다.
        mockMvc.perform(get("/api/dashboard/strategy-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].cashAmount").value(4599443.9300))
                .andExpect(jsonPath("$.data[0].totalAssetAmount").value(4999943.9300));

        // DB 직접 검증
        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(portfolio.getCashAmount())
                .isEqualByComparingTo("4599443.9300");
        PortfolioPositionEntity position = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(instance.getId(), "005930").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(position.getQuantity()).isEqualByComparingTo("5");
    }

    private void linkWatchlist(StrategyInstanceEntity instance, AssetMasterEntity asset) {
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
