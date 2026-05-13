package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.node.ObjectNode;

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

class DecisionLogApiTest extends AdminCatalogApiIntegrationTestSupport {

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
    void listFiltersByStatusAndDate() throws Exception {
        StrategyInstanceEntity instance = createInstance("KR 모멘텀 A");

        seedDecision(instance, "EXECUTE", "5월 8일",
                OffsetDateTime.of(2026, 5, 8, 0, 5, 0, 0, ZoneOffset.UTC), 1);
        seedDecision(instance, "HOLD", "5월 9일",
                OffsetDateTime.of(2026, 5, 9, 0, 5, 0, 0, ZoneOffset.UTC), 0);

        mockMvc.perform(get("/api/trade-decisions")
                        .param("strategyInstanceId", instance.getId().toString())
                        .param("cycleStatus", "EXECUTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].cycleStatus").value("EXECUTE"))
                .andExpect(jsonPath("$.data[0].instanceName").value("KR 모멘텀 A"))
                .andExpect(jsonPath("$.data[0].orderCount").value(1));
    }

    @Test
    void detailIncludesPromptModelTokensAndOrderLinkage() throws Exception {
        StrategyInstanceEntity instance = createInstance("KR 모멘텀 A");
        TradeDecisionLogEntity decision = seedDecision(
                instance, "EXECUTE", "삼성전자 5주 매수",
                OffsetDateTime.of(2026, 5, 11, 0, 35, 0, 0, ZoneOffset.UTC), 1);

        mockMvc.perform(get("/api/trade-decisions/{id}", decision.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(decision.getId().toString()))
                .andExpect(jsonPath("$.data.cycleStatus").value("EXECUTE"))
                .andExpect(jsonPath("$.data.summary").value("삼성전자 5주 매수"))
                .andExpect(jsonPath("$.data.requestText").value("LLM request..."))
                .andExpect(jsonPath("$.data.responseText").value("LLM response..."))
                .andExpect(jsonPath("$.data.modelName").value("gpt-5.5"))
                .andExpect(jsonPath("$.data.engineName").value("openai"))
                .andExpect(jsonPath("$.data.inputTokens").value(1821))
                .andExpect(jsonPath("$.data.outputTokens").value(226))
                .andExpect(jsonPath("$.data.estimatedCost").value(0.0213))
                .andExpect(jsonPath("$.data.callStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.parsedDecision.cycleStatus").value("EXECUTE"))
                .andExpect(jsonPath("$.data.parsedDecision.orders.length()").value(1))
                .andExpect(jsonPath("$.data.parsedDecision.orders[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data.settingsSnapshot.promptVersionId").exists())
                .andExpect(jsonPath("$.data.orderIntents.length()").value(1))
                .andExpect(jsonPath("$.data.orderIntents[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data.orders.length()").value(1))
                .andExpect(jsonPath("$.data.orders[0].orderStatus").value("filled"))
                .andExpect(jsonPath("$.data.cycleStartedAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
    }

    @Test
    void detailUnknownReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/trade-decisions/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private TradeDecisionLogEntity seedDecision(
            StrategyInstanceEntity instance,
            String status,
            String summary,
            OffsetDateTime cycleStartedAt,
            int orderCount) {
        TradeCycleLogEntity cycleLog = new TradeCycleLogEntity();
        cycleLog.setStrategyInstance(instance);
        cycleLog.setCycleStartedAt(cycleStartedAt);
        cycleLog.setCycleFinishedAt(cycleStartedAt.plusSeconds(2));
        cycleLog.setBusinessDate(cycleStartedAt.toLocalDate());
        cycleLog.setCycleStage("COMPLETED");
        cycleLog = tradeCycleLogRepository.saveAndFlush(cycleLog);

        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("promptVersionId", java.util.UUID.randomUUID().toString());
        snapshot.put("tradingModelProfileId", java.util.UUID.randomUUID().toString());

        TradeDecisionLogEntity decision = new TradeDecisionLogEntity();
        decision.setTradeCycleLog(cycleLog);
        decision.setStrategyInstance(instance);
        decision.setCycleStartedAt(cycleStartedAt);
        decision.setCycleFinishedAt(cycleStartedAt.plusSeconds(2));
        decision.setBusinessDate(cycleStartedAt.toLocalDate());
        decision.setCycleStatus(status);
        decision.setSummary(summary);
        decision.setSettingsSnapshotJson(snapshot);
        decision.setEngineName("openai");
        decision.setModelName("gpt-5.5");
        decision.setRequestText("LLM request...");
        decision.setResponseText("LLM response...");
        decision.setStdoutText("");
        decision.setStderrText("");
        decision.setInputTokens(1821);
        decision.setOutputTokens(226);
        decision.setEstimatedCost(new BigDecimal("0.02130000"));
        decision.setCallStatus("SUCCESS");
        decision = tradeDecisionLogRepository.saveAndFlush(decision);

        for (int i = 0; i < orderCount; i++) {
            TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
            intent.setTradeDecisionLog(decision);
            intent.setSequenceNo(i + 1);
            intent.setSymbolCode("005930");
        intent.setSymbolName("Test Stock");
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
            order.setClientOrderId("client-" + java.util.UUID.randomUUID());
            order.setExecutionMode("paper");
            order.setOrderStatus("filled");
            order.setRequestedQuantity(new BigDecimal("5.00000000"));
            order.setRequestedPrice(new BigDecimal("81000.00000000"));
            order.setFilledQuantity(new BigDecimal("5.00000000"));
            order.setAvgFilledPrice(new BigDecimal("81000.00000000"));
            order.setRequestedAt(cycleStartedAt.plusSeconds(1));
            order.setAcceptedAt(cycleStartedAt.plusSeconds(1));
            order.setFilledAt(cycleStartedAt.plusSeconds(1));
            tradeOrderRepository.saveAndFlush(order);
        }
        return decision;
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
