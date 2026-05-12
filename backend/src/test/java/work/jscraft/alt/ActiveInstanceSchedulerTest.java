package work.jscraft.alt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveInstanceSchedulerTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        // 2026-05-11 10:00 KST (월요일 장중)
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void onlyActiveInstancesGetCyclesStarted() {
        StrategyInstanceEntity active = createActiveInstance("KR 모멘텀 A", "paper");
        StrategyInstanceEntity draft = createInstanceWithState("KR 모멘텀 B", "paper", "draft", null);
        StrategyInstanceEntity inactive = createInstanceWithState("KR 모멘텀 C", "paper", "inactive", null);
        StrategyInstanceEntity autoPaused = createInstanceWithState("KR 모멘텀 D", "paper", "active", "reconcile_failed");

        List<StrategyInstanceEntity> activeFound =
                strategyInstanceRepository.findByLifecycleStateAndAutoPausedReasonIsNull("active");
        assertThat(activeFound).extracting(StrategyInstanceEntity::getId).containsExactly(active.getId());

        for (StrategyInstanceEntity instance : List.of(active, draft, inactive, autoPaused)) {
            CycleResult result = orchestrator.runOnce(instance.getId(), "test-worker");
            if (instance.getId().equals(active.getId())) {
                assertThat(result.status()).isEqualTo(CycleResult.Status.COMPLETED);
                assertThat(result.cycleStatus()).isEqualTo("HOLD");
            } else {
                assertThat(result.status()).isEqualTo(CycleResult.Status.SKIPPED);
            }
        }

        List<TradeCycleLogEntity> cycleLogs = tradeCycleLogRepository.findAll();
        assertThat(cycleLogs).hasSize(1);
        TradeCycleLogEntity log = cycleLogs.get(0);
        assertThat(log.getStrategyInstance().getId()).isEqualTo(active.getId());
        assertThat(log.getCycleStage()).isEqualTo("COMPLETED");
    }

    @Test
    void outOfWindowSkipsActiveInstance() {
        StrategyInstanceEntity active = createActiveInstance("KR 모멘텀 A", "paper");
        // 토요일 10시 KST -> 시장 시간대 밖
        mutableClock.setInstant(Instant.parse("2026-05-09T01:00:00Z"));

        CycleResult result = orchestrator.runOnce(active.getId(), "test-worker");

        assertThat(result.status()).isEqualTo(CycleResult.Status.SKIPPED);
        assertThat(result.skipReason()).isEqualTo(CycleExecutionOrchestrator.SkipReason.OUT_OF_WINDOW);
        assertThat(tradeCycleLogRepository.findAll()).isEmpty();
    }

    @Test
    void unknownInstanceIdReturnsSkipped() {
        CycleResult result = orchestrator.runOnce(UUID.randomUUID(), "test-worker");
        assertThat(result.status()).isEqualTo(CycleResult.Status.SKIPPED);
        assertThat(result.skipReason()).isEqualTo(CycleExecutionOrchestrator.SkipReason.INSTANCE_NOT_FOUND);
    }
}
