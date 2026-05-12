package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
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

class TradeHistoryApiTest extends AdminCatalogApiIntegrationTestSupport {

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
    void listReturnsRecent7DaysByDefaultAndOrdersByRequestedAtDesc() throws Exception {
        StrategyInstanceEntity instance = createInstance("KR 모멘텀 A");

        seedOrder(instance, "005930", "BUY", "filled",
                OffsetDateTime.of(2026, 5, 11, 0, 35, 0, 0, ZoneOffset.UTC));
        seedOrder(instance, "000660", "SELL", "filled",
                OffsetDateTime.of(2026, 5, 9, 0, 5, 0, 0, ZoneOffset.UTC));
        // 8일 전 — 기본 7일 범위 밖
        seedOrder(instance, "005930", "BUY", "filled",
                OffsetDateTime.of(2026, 5, 2, 0, 5, 0, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/trade-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data[1].symbolCode").value("000660"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    void listFiltersBySymbolStatusInstanceAndDateRange() throws Exception {
        StrategyInstanceEntity instanceA = createInstance("KR 모멘텀 A");
        StrategyInstanceEntity instanceB = createInstance("KR 모멘텀 B");

        seedOrder(instanceA, "005930", "BUY", "filled",
                OffsetDateTime.of(2026, 5, 8, 0, 35, 0, 0, ZoneOffset.UTC));
        seedOrder(instanceA, "000660", "BUY", "filled",
                OffsetDateTime.of(2026, 5, 8, 1, 5, 0, 0, ZoneOffset.UTC));
        seedOrder(instanceA, "005930", "SELL", "rejected",
                OffsetDateTime.of(2026, 5, 8, 2, 5, 0, 0, ZoneOffset.UTC));
        seedOrder(instanceB, "005930", "BUY", "filled",
                OffsetDateTime.of(2026, 5, 8, 3, 5, 0, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/trade-orders")
                        .param("strategyInstanceId", instanceA.getId().toString())
                        .param("symbolCode", "005930")
                        .param("orderStatus", "filled")
                        .param("dateFrom", "2026-05-08")
                        .param("dateTo", "2026-05-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].instanceName").value("KR 모멘텀 A"))
                .andExpect(jsonPath("$.data[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data[0].orderStatus").value("filled"));
    }

    @Test
    void detailIncludesPortfolioAfterAndKstTimes() throws Exception {
        StrategyInstanceEntity instance = createInstance("KR 모멘텀 A");
        TradeOrderEntity order = seedOrder(instance, "005930", "BUY", "filled",
                OffsetDateTime.of(2026, 5, 11, 0, 35, 1, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/trade-orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.data.executionMode").value("paper"))
                .andExpect(jsonPath("$.data.orderStatus").value("filled"))
                .andExpect(jsonPath("$.data.requestedAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
    }

    @Test
    void detailUnknownReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/trade-orders/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private TradeOrderEntity seedOrder(
            StrategyInstanceEntity instance,
            String symbolCode,
            String side,
            String status,
            OffsetDateTime requestedAt) {
        TradeCycleLogEntity cycleLog = new TradeCycleLogEntity();
        cycleLog.setStrategyInstance(instance);
        cycleLog.setCycleStartedAt(requestedAt);
        cycleLog.setCycleFinishedAt(requestedAt.plusSeconds(2));
        cycleLog.setBusinessDate(requestedAt.toLocalDate());
        cycleLog.setCycleStage("COMPLETED");
        cycleLog = tradeCycleLogRepository.saveAndFlush(cycleLog);

        TradeDecisionLogEntity decision = new TradeDecisionLogEntity();
        decision.setTradeCycleLog(cycleLog);
        decision.setStrategyInstance(instance);
        decision.setCycleStartedAt(requestedAt);
        decision.setCycleFinishedAt(requestedAt.plusSeconds(2));
        decision.setBusinessDate(requestedAt.toLocalDate());
        decision.setCycleStatus("EXECUTE");
        decision.setSummary(symbolCode + " " + side);
        decision.setSettingsSnapshotJson(objectMapper.createObjectNode());
        decision = tradeDecisionLogRepository.saveAndFlush(decision);

        TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
        intent.setTradeDecisionLog(decision);
        intent.setSequenceNo(1);
        intent.setSymbolCode(symbolCode);
        intent.setSide(side);
        intent.setQuantity(new BigDecimal("5.00000000"));
        intent.setOrderType("LIMIT");
        intent.setPrice(new BigDecimal("80000.00000000"));
        intent.setRationale("test");
        intent.setEvidenceJson(objectMapper.createArrayNode());
        intent = tradeOrderIntentRepository.saveAndFlush(intent);

        TradeOrderEntity order = new TradeOrderEntity();
        order.setTradeOrderIntent(intent);
        order.setStrategyInstance(instance);
        order.setClientOrderId("client-" + java.util.UUID.randomUUID());
        order.setExecutionMode("paper");
        order.setOrderStatus(status);
        order.setRequestedQuantity(new BigDecimal("5.00000000"));
        order.setRequestedPrice(new BigDecimal("80000.00000000"));
        order.setFilledQuantity(new BigDecimal("5.00000000"));
        order.setAvgFilledPrice(new BigDecimal("80000.00000000"));
        order.setRequestedAt(requestedAt);
        order.setAcceptedAt(requestedAt);
        order.setFilledAt("filled".equals(status) ? requestedAt : null);
        order.setPortfolioAfterJson(objectMapper.createObjectNode().put("cashAmount", 1000000));
        return tradeOrderRepository.saveAndFlush(order);
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
