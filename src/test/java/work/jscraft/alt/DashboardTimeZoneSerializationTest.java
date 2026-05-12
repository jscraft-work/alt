package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardTimeZoneSerializationTest extends AdminCatalogApiIntegrationTestSupport {

    @Autowired
    private OpsEventRepository opsEventRepository;

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
    void overviewSerializesTimestampsInKstOffset() throws Exception {
        StrategyInstanceEntity instance = createActiveInstance();
        OffsetDateTime cycleStartedAtUtc = OffsetDateTime.of(2026, 5, 11, 1, 5, 0, 0, ZoneOffset.UTC);
        seedDecision(instance, cycleStartedAtUtc);

        String body = mockMvc.perform(get("/api/dashboard/strategy-overview"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode card = objectMapper.readTree(body).path("data").get(0);
        String latestDecisionAt = card.path("latestDecisionAt").asText();
        assertThat(latestDecisionAt).endsWith("+09:00");
        assertThat(OffsetDateTime.parse(latestDecisionAt))
                .isEqualTo(cycleStartedAtUtc.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }

    @Test
    void systemStatusSerializesTimestampsInKstOffset() throws Exception {
        OffsetDateTime occurredAtUtc = OffsetDateTime.of(2026, 5, 11, 1, 59, 30, 0, ZoneOffset.UTC);
        OpsEventEntity event = new OpsEventEntity();
        event.setServiceName("marketdata");
        event.setStatusCode("ok");
        event.setOccurredAt(occurredAtUtc);
        event.setBusinessDate(LocalDate.of(2026, 5, 11));
        event.setEventType("HEALTHCHECK");
        opsEventRepository.saveAndFlush(event);

        String body = mockMvc.perform(get("/api/dashboard/system-status"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode marketdata = findService(objectMapper.readTree(body).path("data"), "marketdata");
        assertThat(marketdata.path("lastSuccessAt").asText()).endsWith("+09:00");
        assertThat(marketdata.path("occurredAt").asText()).endsWith("+09:00");
    }

    private StrategyInstanceEntity createActiveInstance() {
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("kst-template", "prompt", modelProfile);

        StrategyInstanceEntity instance = new StrategyInstanceEntity();
        instance.setStrategyTemplate(template);
        instance.setName("KST 인스턴스");
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

    private void seedDecision(StrategyInstanceEntity instance, OffsetDateTime cycleStartedAtUtc) {
        OffsetDateTime cycleFinishedAtUtc = cycleStartedAtUtc.plusSeconds(2);

        TradeCycleLogEntity cycleLog = new TradeCycleLogEntity();
        cycleLog.setStrategyInstance(instance);
        cycleLog.setCycleStartedAt(cycleStartedAtUtc);
        cycleLog.setCycleFinishedAt(cycleFinishedAtUtc);
        cycleLog.setBusinessDate(LocalDate.of(2026, 5, 11));
        cycleLog.setCycleStage("COMPLETED");
        cycleLog = tradeCycleLogRepository.saveAndFlush(cycleLog);

        TradeDecisionLogEntity decision = new TradeDecisionLogEntity();
        decision.setTradeCycleLog(cycleLog);
        decision.setStrategyInstance(instance);
        decision.setCycleStartedAt(cycleStartedAtUtc);
        decision.setCycleFinishedAt(cycleFinishedAtUtc);
        decision.setBusinessDate(LocalDate.of(2026, 5, 11));
        decision.setCycleStatus("HOLD");
        decision.setSummary("KST 직렬화 검증");
        decision.setSettingsSnapshotJson(objectMapper.createObjectNode());
        tradeDecisionLogRepository.saveAndFlush(decision);
    }

    private JsonNode findService(JsonNode data, String name) {
        for (JsonNode node : data) {
            if (name.equals(node.path("serviceName").asText())) {
                return node;
            }
        }
        throw new AssertionError("serviceName not found: " + name);
    }
}
