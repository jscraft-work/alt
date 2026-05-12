package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StrategyOverviewApiTest extends AdminCatalogApiIntegrationTestSupport {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @Autowired
    private TradeDecisionLogRepository tradeDecisionLogRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
    }

    @Test
    void anonymousCallReturnsCardsForAllInstances() throws Exception {
        StrategyInstanceEntity activeInstance = seedActiveInstanceWithPortfolio(
                "KR 모멘텀 A",
                "paper",
                new BigDecimal("10000000.0000"),
                new BigDecimal("6200000.0000"),
                new BigDecimal("10120000.0000"),
                new BigDecimal("120000.0000"));
        seedWatchlist(activeInstance, 2);
        seedLatestDecision(activeInstance, "HOLD", "관망 유지");

        seedDraftInstance("KR 모멘텀 B", "paper");

        String body = mockMvc.perform(get("/api/dashboard/strategy-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        JsonNode active = findCard(data, activeInstance.getId().toString());
        assertThat(active.path("name").asText()).isEqualTo("KR 모멘텀 A");
        assertThat(active.path("executionMode").asText()).isEqualTo("paper");
        assertThat(active.path("lifecycleState").asText()).isEqualTo("active");
        assertThat(active.path("budgetAmount").decimalValue()).isEqualByComparingTo("10000000.0000");
        assertThat(active.path("cashAmount").decimalValue()).isEqualByComparingTo("6200000.0000");
        assertThat(active.path("totalAssetAmount").decimalValue()).isEqualByComparingTo("10120000.0000");
        assertThat(active.path("todayRealizedPnl").decimalValue()).isEqualByComparingTo("120000.0000");
        assertThat(active.path("latestDecisionStatus").asText()).isEqualTo("HOLD");
        assertThat(active.path("latestDecisionAt").asText()).endsWith("+09:00");
        assertThat(active.path("watchlistCount").asLong()).isEqualTo(2L);
    }

    @Test
    void filterByLifecycleState() throws Exception {
        seedActiveInstanceWithPortfolio(
                "KR 모멘텀 A",
                "paper",
                new BigDecimal("10000000.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        StrategyInstanceEntity draftInstance = seedDraftInstance("KR 모멘텀 B", "paper");

        mockMvc.perform(get("/api/dashboard/strategy-overview").param("lifecycleState", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].strategyInstanceId").value(draftInstance.getId().toString()));
    }

    private JsonNode findCard(JsonNode data, String id) {
        for (JsonNode node : data) {
            if (id.equals(node.path("strategyInstanceId").asText())) {
                return node;
            }
        }
        throw new AssertionError("strategyInstanceId not found: " + id);
    }

    private StrategyInstanceEntity seedActiveInstanceWithPortfolio(
            String name,
            String executionMode,
            BigDecimal budget,
            BigDecimal cash,
            BigDecimal totalAsset,
            BigDecimal realizedPnl) {
        StrategyInstanceEntity instance = createInstance(name, executionMode, "active", budget);
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(totalAsset);
        portfolio.setRealizedPnlToday(realizedPnl);
        portfolioRepository.saveAndFlush(portfolio);
        return instance;
    }

    private StrategyInstanceEntity seedDraftInstance(String name, String executionMode) {
        return createInstance(name, executionMode, "draft", new BigDecimal("5000000.0000"));
    }

    private StrategyInstanceEntity createInstance(String name, String executionMode, String lifecycleState, BigDecimal budget) {
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate(name + "-template", "prompt", modelProfile);

        StrategyInstanceEntity instance = new StrategyInstanceEntity();
        instance.setStrategyTemplate(template);
        instance.setName(name);
        instance.setLifecycleState(lifecycleState);
        instance.setExecutionMode(executionMode);
        instance.setBudgetAmount(budget);
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

    private void seedWatchlist(StrategyInstanceEntity instance, int count) {
        for (int i = 0; i < count; i++) {
            String code = String.format("00593%d", i);
            var asset = createAsset(code, "Asset " + i, null);
            StrategyInstanceWatchlistRelationEntity relation = new StrategyInstanceWatchlistRelationEntity();
            relation.setStrategyInstance(instance);
            relation.setAssetMaster(asset);
            watchlistRelationRepository.saveAndFlush(relation);
        }
    }

    private void seedLatestDecision(StrategyInstanceEntity instance, String status, String summary) {
        OffsetDateTime cycleStartedAt = OffsetDateTime.of(2026, 5, 11, 1, 5, 0, 0, ZoneOffset.UTC);
        OffsetDateTime cycleFinishedAt = cycleStartedAt.plusSeconds(2);

        TradeCycleLogEntity cycleLog = new TradeCycleLogEntity();
        cycleLog.setStrategyInstance(instance);
        cycleLog.setCycleStartedAt(cycleStartedAt);
        cycleLog.setCycleFinishedAt(cycleFinishedAt);
        cycleLog.setBusinessDate(LocalDate.of(2026, 5, 11));
        cycleLog.setCycleStage("COMPLETED");
        cycleLog = tradeCycleLogRepository.saveAndFlush(cycleLog);

        TradeDecisionLogEntity decision = new TradeDecisionLogEntity();
        decision.setTradeCycleLog(cycleLog);
        decision.setStrategyInstance(instance);
        decision.setCycleStartedAt(cycleStartedAt);
        decision.setCycleFinishedAt(cycleFinishedAt);
        decision.setBusinessDate(LocalDate.of(2026, 5, 11));
        decision.setCycleStatus(status);
        decision.setSummary(summary);
        decision.setSettingsSnapshotJson(objectMapper.createObjectNode());
        tradeDecisionLogRepository.saveAndFlush(decision);
    }
}
