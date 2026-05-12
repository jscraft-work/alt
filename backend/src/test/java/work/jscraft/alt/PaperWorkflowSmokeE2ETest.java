package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

class PaperWorkflowSmokeE2ETest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void fullPaperWorkflowFromLoginToDashboard() throws Exception {
        // 1. 로그인 가능한 admin 생성 + 로그인
        createAdminUser();
        LoginCookies adminLogin = login();

        // 로그인 직후 /api/auth/me 조회
        mockMvc.perform(get("/api/auth/me").cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("admin"))
                .andExpect(jsonPath("$.data.roleCode").value("ADMIN"));

        // 2. 운영자 설정: paper 인스턴스 + 워치리스트 + 초기 잔고 준비
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 SMOKE", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        StrategyInstanceWatchlistRelationEntity relation = new StrategyInstanceWatchlistRelationEntity();
        relation.setStrategyInstance(instance);
        relation.setAssetMaster(samsung);
        watchlistRelationRepository.saveAndFlush(relation);
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(new BigDecimal("5000000.0000"));
        portfolio.setTotalAssetAmount(new BigDecimal("5000000.0000"));
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);

        // 3. (수집 더블) 시세 시드 — paper LIMIT 주문이라 직접 사용 안하지만 차트 API용
        // 4. fake 결정엔진이 EXECUTE 반환하도록 prime
        fakeTradingDecisionEngine.primeSuccess("""
                {
                  "cycleStatus":"EXECUTE",
                  "summary":"삼성전자 5주 매수",
                  "orders":[
                    {"sequenceNo":1,"symbolCode":"005930","side":"BUY","quantity":5,"orderType":"LIMIT","price":80000,"rationale":"smoke"}
                  ]
                }
                """);

        // 5. paper 사이클 수행
        CycleResult result = orchestrator.runOnce(instance.getId(), "smoke-worker");
        assertThat(result.status()).isEqualTo(CycleResult.Status.COMPLETED);

        // 6. 비로그인 대시보드 조회 → 갱신된 portfolio가 반영되어야 한다
        mockMvc.perform(get("/api/dashboard/instances/{id}", instance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolio.cashAmount").value(4600000.0000))
                .andExpect(jsonPath("$.data.positions.length()").value(1))
                .andExpect(jsonPath("$.data.positions[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data.recentOrders[0].orderStatus").value("filled"));

        // 7. trade-orders 공개 API에서도 주문이 확인된다
        mockMvc.perform(get("/api/trade-orders")
                        .param("strategyInstanceId", instance.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data[0].orderStatus").value("filled"));

        // 8. 로그아웃 사이클 (인증 경계 정상)
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                        .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue()))
                .andExpect(status().isOk());
    }
}
