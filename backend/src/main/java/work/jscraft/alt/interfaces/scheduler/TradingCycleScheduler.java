package work.jscraft.alt.interfaces.scheduler;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import work.jscraft.alt.common.config.ApplicationProfiles;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;

@Component
@Profile(ApplicationProfiles.TRADING_WORKER)
public class TradingCycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(TradingCycleScheduler.class);

    private final StrategyInstanceRepository strategyInstanceRepository;
    private final CycleExecutionOrchestrator orchestrator;
    private final String workerId;

    public TradingCycleScheduler(
            StrategyInstanceRepository strategyInstanceRepository,
            CycleExecutionOrchestrator orchestrator) {
        this.strategyInstanceRepository = strategyInstanceRepository;
        this.orchestrator = orchestrator;
        this.workerId = "trading-worker-" + UUID.randomUUID();
    }

    @Scheduled(
            initialDelayString = "${app.trading.cycle.initial-delay-ms:60000}",
            fixedDelayString = "${app.trading.cycle.poll-interval-ms:30000}")
    public void dispatchActiveCycles() {
        List<StrategyInstanceEntity> activeInstances =
                strategyInstanceRepository.findByLifecycleStateAndAutoPausedReasonIsNull(
                        CycleExecutionOrchestrator.LIFECYCLE_STATE_ACTIVE);
        for (StrategyInstanceEntity instance : activeInstances) {
            try {
                CycleResult result = orchestrator.runOnce(instance.getId(), workerId);
                if (result.status() == CycleResult.Status.SKIPPED) {
                    log.debug("cycle skipped instance={} reason={}", instance.getId(), result.skipReason());
                } else if (result.status() == CycleResult.Status.FAILED) {
                    log.warn("cycle failed instance={} message={}", instance.getId(), result.failureMessage());
                }
            } catch (RuntimeException ex) {
                log.warn("cycle dispatch error instance={} message={}", instance.getId(), ex.getMessage());
            }
        }
    }

    public String workerId() {
        return workerId;
    }
}
