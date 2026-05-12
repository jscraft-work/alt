package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstanceDashboardApiTest extends AdminCatalogApiIntegrationTestSupport {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioPositionRepository portfolioPositionRepository;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @Autowired
    private TradeDecisionLogRepository tradeDecisionLogRepository;

    @Autowired
    private TradeOrderIntentRepository tradeOrderIntentRepository;

    @Autowired
    private TradeOrderRepository tradeOrderRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
    }

    @Test
    void instanceDashboardReturnsPortfolioPositionsAndRecentActivity() throws Exception {
        StrategyInstanceEntity instance = createInstance("KR 모멘텀 A");

        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);

        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(new BigDecimal("6200000.0000"));
        portfolio.setTotalAssetAmount(new BigDecimal("10120000.0000"));
        portfolio.setRealizedPnlToday(new BigDecimal("120000.0000"));
        portfolioRepository.saveAndFlush(portfolio);

        PortfolioPositionEntity position = new PortfolioPositionEntity();
        position.setStrategyInstanceId(instance.getId());
        position.setSymbolCode(samsung.getSymbolCode());
        position.setQuantity(new BigDecimal("12.00000000"));
        position.setAvgBuyPrice(new BigDecimal("81200.00000000"));
        position.setLastMarkPrice(new BigDecimal("82600.00000000"));
        position.setUnrealizedPnl(new BigDecimal("16800.0000"));
        portfolioPositionRepository.saveAndFlush(position);

        TradeCycleLogEntity cycleLog = new TradeCycleLogEntity();
        cycleLog.setStrategyInstance(instance);
        cycleLog.setCycleStartedAt(OffsetDateTime.of(2026, 5, 11, 1, 5, 0, 0, ZoneOffset.UTC));
        cycleLog.setCycleFinishedAt(OffsetDateTime.of(2026, 5, 11, 1, 5, 2, 0, ZoneOffset.UTC));
        cycleLog.setBusinessDate(LocalDate.of(2026, 5, 11));
        cycleLog.setCycleStage("COMPLETED");
        cycleLog = tradeCycleLogRepository.saveAndFlush(cycleLog);

        TradeDecisionLogEntity decision = new TradeDecisionLogEntity();
        decision.setTradeCycleLog(cycleLog);
        decision.setStrategyInstance(instance);
        decision.setCycleStartedAt(cycleLog.getCycleStartedAt());
        decision.setCycleFinishedAt(cycleLog.getCycleFinishedAt());
        decision.setBusinessDate(LocalDate.of(2026, 5, 11));
        decision.setCycleStatus("EXECUTE");
        decision.setSummary("삼성전자 5주 매수");
        decision.setSettingsSnapshotJson(objectMapper.createObjectNode());
        decision = tradeDecisionLogRepository.saveAndFlush(decision);

        TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
        intent.setTradeDecisionLog(decision);
        intent.setSequenceNo(1);
        intent.setSymbolCode(samsung.getSymbolCode());
        intent.setSide("BUY");
        intent.setQuantity(new BigDecimal("5.00000000"));
        intent.setOrderType("LIMIT");
        intent.setPrice(new BigDecimal("81000.00000000"));
        intent.setRationale("실적 기대");
        intent.setEvidenceJson(objectMapper.createArrayNode());
        intent = tradeOrderIntentRepository.saveAndFlush(intent);

        TradeOrderEntity order = new TradeOrderEntity();
        order.setTradeOrderIntent(intent);
        order.setStrategyInstance(instance);
        order.setClientOrderId("client-1");
        order.setExecutionMode("paper");
        order.setOrderStatus("filled");
        order.setRequestedQuantity(new BigDecimal("5.00000000"));
        order.setRequestedPrice(new BigDecimal("81000.00000000"));
        order.setFilledQuantity(new BigDecimal("5.00000000"));
        order.setAvgFilledPrice(new BigDecimal("81000.00000000"));
        order.setRequestedAt(OffsetDateTime.of(2026, 5, 11, 0, 35, 1, 0, ZoneOffset.UTC));
        order.setAcceptedAt(order.getRequestedAt());
        order.setFilledAt(order.getRequestedAt());
        tradeOrderRepository.saveAndFlush(order);

        mockMvc.perform(get("/api/dashboard/instances/{id}", instance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instance.id").value(instance.getId().toString()))
                .andExpect(jsonPath("$.data.instance.name").value("KR 모멘텀 A"))
                .andExpect(jsonPath("$.data.instance.executionMode").value("paper"))
                .andExpect(jsonPath("$.data.portfolio.cashAmount").value(6200000.0000))
                .andExpect(jsonPath("$.data.portfolio.totalAssetAmount").value(10120000.0000))
                .andExpect(jsonPath("$.data.portfolio.realizedPnlToday").value(120000.0000))
                .andExpect(jsonPath("$.data.positions.length()").value(1))
                .andExpect(jsonPath("$.data.positions[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data.positions[0].symbolName").value("삼성전자"))
                .andExpect(jsonPath("$.data.positions[0].quantity").value(12.00000000))
                .andExpect(jsonPath("$.data.systemStatus.length()").value(5))
                .andExpect(jsonPath("$.data.latestDecision.decisionLogId").value(decision.getId().toString()))
                .andExpect(jsonPath("$.data.latestDecision.cycleStatus").value("EXECUTE"))
                .andExpect(jsonPath("$.data.latestDecision.summary").value("삼성전자 5주 매수"))
                .andExpect(jsonPath("$.data.recentOrders.length()").value(1))
                .andExpect(jsonPath("$.data.recentOrders[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data.recentOrders[0].side").value("BUY"))
                .andExpect(jsonPath("$.data.recentOrders[0].orderStatus").value("filled"))
                .andExpect(jsonPath("$.data.recentOrders[0].requestedQuantity").value(5.00000000));
    }

    @Test
    void unknownInstanceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/dashboard/instances/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private StrategyInstanceEntity createInstance(String name) {
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate(name + "-template", "prompt", modelProfile);

        StrategyInstanceEntity instance = new StrategyInstanceEntity();
        instance.setStrategyTemplate(template);
        instance.setName(name);
        instance.setLifecycleState("active");
        instance.setExecutionMode("paper");
        instance.setBudgetAmount(new BigDecimal("10000000.0000"));
        instance.setTradingModelProfile(modelProfile);
        instance = strategyInstanceRepository.saveAndFlush(instance);

        StrategyInstancePromptVersionEntity promptVersion = new StrategyInstancePromptVersionEntity();
        promptVersion.setStrategyInstance(instance);
        promptVersion.setVersionNo(1);
        promptVersion.setPromptText("prompt");
        promptVersion.setChangeNote("init");
        promptVersion = strategyInstancePromptVersionRepository.saveAndFlush(promptVersion);

        instance.setCurrentPromptVersion(promptVersion);
        return strategyInstanceRepository.saveAndFlush(instance);
    }
}
