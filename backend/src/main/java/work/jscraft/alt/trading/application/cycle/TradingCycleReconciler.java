package work.jscraft.alt.trading.application.cycle;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import work.jscraft.alt.common.config.ApplicationProfiles;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;

/**
 * State-based reconciler. The DB column strategy_instance.lifecycle_state is the source of truth;
 * scheduled_tasks is treated as a derived projection. Periodically: register missing active
 * instances, cancel stale schedules. Runs only on trading-worker JVMs.
 */
@Component
@Profile(ApplicationProfiles.TRADING_WORKER)
public class TradingCycleReconciler {

    private static final Logger log = LoggerFactory.getLogger(TradingCycleReconciler.class);
    private static final String LIFECYCLE_ACTIVE = "active";

    private final SchedulerClient schedulerClient;
    private final StrategyInstanceRepository strategyInstanceRepository;

    public TradingCycleReconciler(
            SchedulerClient schedulerClient,
            StrategyInstanceRepository strategyInstanceRepository) {
        this.schedulerClient = schedulerClient;
        this.strategyInstanceRepository = strategyInstanceRepository;
    }

    @Scheduled(
            initialDelayString = "${app.trading.reconcile.initial-delay-ms:5000}",
            fixedDelayString = "${app.trading.reconcile.interval-ms:60000}")
    public void reconcile() {
        List<StrategyInstanceEntity> activeInstances =
                strategyInstanceRepository.findByLifecycleStateAndAutoPausedReasonIsNull(LIFECYCLE_ACTIVE);

        Set<UUID> activeIds = new HashSet<>();
        for (StrategyInstanceEntity instance : activeInstances) {
            activeIds.add(instance.getId());
            schedule(instance);
        }

        List<ScheduledExecution<CycleScheduleData>> scheduled =
                schedulerClient.getScheduledExecutionsForTask(
                        TradingCycleSchedulerConfig.TASK_NAME, CycleScheduleData.class);
        for (ScheduledExecution<CycleScheduleData> exec : scheduled) {
            UUID instanceId = exec.getData().instanceId();
            if (!activeIds.contains(instanceId)) {
                schedulerClient.cancel(
                        TaskInstanceId.of(TradingCycleSchedulerConfig.TASK_NAME, instanceId.toString()));
                log.info("cycle schedule cancelled instance={}", instanceId);
            }
        }
    }

    private void schedule(StrategyInstanceEntity instance) {
        int cycleMinutes = instance.getStrategyTemplate().getDefaultCycleMinutes();
        CycleScheduleData data = new CycleScheduleData(instance.getId(), cycleMinutes);
        boolean created = schedulerClient.scheduleIfNotExists(
                TradingCycleSchedulerConfig.TRADING_CYCLE_TASK
                        .instance(instance.getId().toString())
                        .data(data)
                        .scheduledTo(Instant.now()));
        if (created) {
            log.info("cycle schedule created instance={} cycleMinutes={}", instance.getId(), cycleMinutes);
        }
    }
}
