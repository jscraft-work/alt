package work.jscraft.alt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;
import work.jscraft.alt.trading.application.cycle.TradingInstanceLock;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;

import static org.assertj.core.api.Assertions.assertThat;

class RedisLockSerialExecutionTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private TradingInstanceLock instanceLock;

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void secondAcquireOnSameInstanceFailsUntilReleased() {
        UUID instanceId = UUID.randomUUID();

        assertThat(instanceLock.tryAcquire(instanceId, "worker-A")).isTrue();
        assertThat(instanceLock.tryAcquire(instanceId, "worker-B")).isFalse();
        assertThat(instanceLock.currentHolder(instanceId)).isEqualTo("worker-A");

        Long ttl = instanceLock.ttlSeconds(instanceId);
        long expectedTtlSec = TradingInstanceLock.TTL.toSeconds();
        assertThat(ttl).isBetween(expectedTtlSec - 5, expectedTtlSec + 5);

        assertThat(instanceLock.release(instanceId, "worker-B")).isFalse();
        assertThat(instanceLock.release(instanceId, "worker-A")).isTrue();

        assertThat(instanceLock.tryAcquire(instanceId, "worker-B")).isTrue();
        instanceLock.release(instanceId, "worker-B");
    }

    @Test
    void concurrentOrchestratorRunsAreSerializedByLock() {
        StrategyInstanceEntity active = createActiveInstance("KR 모멘텀 A", "paper");

        // 외부에서 락을 먼저 점유
        assertThat(instanceLock.tryAcquire(active.getId(), "external-worker")).isTrue();
        try {
            CycleResult result = orchestrator.runOnce(active.getId(), "another-worker");
            assertThat(result.status()).isEqualTo(CycleResult.Status.SKIPPED);
            assertThat(result.skipReason()).isEqualTo(CycleExecutionOrchestrator.SkipReason.LOCK_NOT_ACQUIRED);
        } finally {
            instanceLock.release(active.getId(), "external-worker");
        }

        // 락 해제 후에는 정상 사이클 진행 (기본 fake LLM은 HOLD 반환 → COMPLETED)
        CycleResult retry = orchestrator.runOnce(active.getId(), "another-worker");
        assertThat(retry.status()).isEqualTo(CycleResult.Status.COMPLETED);

        List<TradeCycleLogEntity> logs = tradeCycleLogRepository.findAll();
        assertThat(logs).hasSize(1);
    }

    @Test
    void lockTtlMatchesPolicy() {
        assertThat(TradingInstanceLock.TTL).isEqualTo(Duration.ofMinutes(2));
    }
}
