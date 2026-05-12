package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshot;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshotProvider;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmRequest;
import work.jscraft.alt.trading.application.decision.ParsedDecision;
import work.jscraft.alt.trading.application.decision.ParsedDecision.ParsedOrder;
import work.jscraft.alt.trading.application.decision.TradeDecisionLogService;
import work.jscraft.alt.trading.application.decision.TradeDecisionLogService.LlmUsage;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;

import static org.assertj.core.api.Assertions.assertThat;

class TradeDecisionLogPersistenceTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private TradeDecisionLogService tradeDecisionLogService;

    @Autowired
    private TradeDecisionLogRepository tradeDecisionLogRepository;

    @Autowired
    private TradeCycleLifecycle lifecycle;

    @Autowired
    private SettingsSnapshotProvider snapshotProvider;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
    }

    @Test
    void successPathPersistsExecuteDecisionWithLlmFields() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        UUID cycleId = lifecycle.startCycle(instance);
        SettingsSnapshot snapshot = snapshotProvider.capture(instance.getId());

        LlmRequest request = new LlmRequest(
                UUID.randomUUID(),
                "openclaw",
                "gpt-5.5",
                "the prompt",
                Duration.ofSeconds(30));
        LlmCallResult result = LlmCallResult.success(0,
                "{\"cycleStatus\":\"EXECUTE\",\"summary\":\"매수\",\"orders\":[...]}",
                "");
        ParsedDecision parsed = new ParsedDecision(
                "EXECUTE",
                "매수",
                new BigDecimal("0.7000"),
                List.of(new ParsedOrder(1, "005930", "BUY", new BigDecimal("5"),
                        "LIMIT", new BigDecimal("81000"), "근거", objectMapper.createArrayNode())));

        TradeDecisionLogEntity saved = tradeDecisionLogService.saveSuccess(
                cycleId,
                parsed,
                snapshot,
                objectMapper.createObjectNode().put("instanceId", instance.getId().toString()),
                request,
                result,
                new LlmUsage(1500, 200, new BigDecimal("0.01230000")));

        TradeDecisionLogEntity reloaded = tradeDecisionLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCycleStatus()).isEqualTo("EXECUTE");
        assertThat(reloaded.getSummary()).isEqualTo("매수");
        assertThat(reloaded.getConfidence()).isEqualByComparingTo("0.7000");
        assertThat(reloaded.getEngineName()).isEqualTo("openclaw");
        assertThat(reloaded.getModelName()).isEqualTo("gpt-5.5");
        assertThat(reloaded.getRequestText()).isEqualTo("the prompt");
        assertThat(reloaded.getResponseText()).contains("EXECUTE");
        assertThat(reloaded.getStdoutText()).contains("EXECUTE");
        assertThat(reloaded.getStderrText()).isEqualTo("");
        assertThat(reloaded.getInputTokens()).isEqualTo(1500);
        assertThat(reloaded.getOutputTokens()).isEqualTo(200);
        assertThat(reloaded.getEstimatedCost()).isEqualByComparingTo("0.01230000");
        assertThat(reloaded.getCallStatus()).isEqualTo("SUCCESS");
        assertThat(reloaded.getExitCode()).isEqualTo(0);
        assertThat(reloaded.getSessionId()).isEqualTo(request.sessionId());
    }

    @Test
    void failurePathPersistsFailedDecisionWithReason() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 B", "paper");
        UUID cycleId = lifecycle.startCycle(instance);
        SettingsSnapshot snapshot = snapshotProvider.capture(instance.getId());

        LlmRequest request = new LlmRequest(
                UUID.randomUUID(),
                "openclaw",
                "gpt-5.5",
                "the prompt",
                Duration.ofSeconds(30));
        LlmCallResult result = LlmCallResult.timeout("타임아웃 30s");

        TradeDecisionLogEntity saved = tradeDecisionLogService.saveFailure(
                cycleId,
                snapshot,
                objectMapper.createObjectNode(),
                request,
                result,
                null,
                "LLM_TIMEOUT",
                "subprocess timed out");

        lifecycle.fail(cycleId, "LLM_TIMEOUT", "subprocess timed out");

        TradeDecisionLogEntity reloaded = tradeDecisionLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCycleStatus()).isEqualTo("FAILED");
        assertThat(reloaded.getFailureReason()).isEqualTo("LLM_TIMEOUT");
        assertThat(reloaded.getFailureDetail()).isEqualTo("subprocess timed out");
        assertThat(reloaded.getCallStatus()).isEqualTo("TIMEOUT");
    }
}
