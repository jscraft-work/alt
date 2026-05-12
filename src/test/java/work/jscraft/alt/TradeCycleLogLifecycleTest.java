package work.jscraft.alt;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;

import static org.assertj.core.api.Assertions.assertThat;

class TradeCycleLogLifecycleTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

    @Autowired
    private TradeCycleLifecycle lifecycle;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void paperCycleCompletesWhenLlmReturnsHold() {
        StrategyInstanceEntity paperInstance = createActiveInstance("KR 모멘텀 A", "paper");

        CycleResult result = orchestrator.runOnce(paperInstance.getId(), "test-worker");

        assertThat(result.status()).isEqualTo(CycleResult.Status.COMPLETED);
        assertThat(result.cycleStatus()).isEqualTo("HOLD");

        List<TradeCycleLogEntity> logs = tradeCycleLogRepository.findAll();
        assertThat(logs).hasSize(1);
        TradeCycleLogEntity log = logs.get(0);
        assertThat(log.getId()).isEqualTo(result.cycleLogId());
        assertThat(log.getCycleStage()).isEqualTo(TradeCycleLifecycle.STAGE_COMPLETED);
        assertThat(log.getCycleStartedAt()).isNotNull();
        assertThat(log.getCycleFinishedAt()).isNotNull();
        assertThat(log.getBusinessDate()).isEqualTo("2026-05-11");
    }

    @Test
    void liveCycleRunsReconcileThenCompletesOnHold() {
        StrategyInstanceEntity liveInstance = createActiveLiveInstance("KR 모멘텀 LIVE");

        CycleResult result = orchestrator.runOnce(liveInstance.getId(), "test-worker");

        // reconcile에 미확정 주문이 없으면 success → HOLD 응답이면 COMPLETED
        assertThat(result.status()).isEqualTo(CycleResult.Status.COMPLETED);
        assertThat(result.cycleStatus()).isEqualTo("HOLD");

        TradeCycleLogEntity log = tradeCycleLogRepository.findById(result.cycleLogId()).orElseThrow();
        assertThat(log.getCycleStage()).isEqualTo(TradeCycleLifecycle.STAGE_COMPLETED);
    }

    @Test
    void lifecycleCompleteAndFailTransitions() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 X", "paper");
        java.util.UUID cycleId = lifecycle.startCycle(instance);
        lifecycle.advance(cycleId, TradeCycleLifecycle.STAGE_INPUT_LOADING);
        lifecycle.complete(cycleId);

        TradeCycleLogEntity completed = tradeCycleLogRepository.findById(cycleId).orElseThrow();
        assertThat(completed.getCycleStage()).isEqualTo(TradeCycleLifecycle.STAGE_COMPLETED);
        assertThat(completed.getCycleFinishedAt()).isNotNull();

        java.util.UUID failed = lifecycle.startCycle(instance);
        lifecycle.fail(failed, "INPUT_FAILED", "데이터 부족");
        TradeCycleLogEntity failedLog = tradeCycleLogRepository.findById(failed).orElseThrow();
        assertThat(failedLog.getCycleStage()).isEqualTo(TradeCycleLifecycle.STAGE_FAILED);
        assertThat(failedLog.getFailureReason()).isEqualTo("INPUT_FAILED");
        assertThat(failedLog.getFailureDetail()).isEqualTo("데이터 부족");
        assertThat(failedLog.getCycleFinishedAt()).isNotNull();
    }
}
