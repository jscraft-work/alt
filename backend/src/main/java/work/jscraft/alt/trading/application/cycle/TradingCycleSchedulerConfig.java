package work.jscraft.alt.trading.application.cycle;

import java.util.UUID;

import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskDescriptor;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import work.jscraft.alt.common.config.ApplicationProfiles;

@Configuration
@Profile(ApplicationProfiles.TRADING_WORKER)
public class TradingCycleSchedulerConfig {

    public static final String TASK_NAME = "trading-cycle";

    public static final TaskDescriptor<CycleScheduleData> TRADING_CYCLE_TASK =
            TaskDescriptor.of(TASK_NAME, CycleScheduleData.class);

    private static final Logger log = LoggerFactory.getLogger(TradingCycleSchedulerConfig.class);

    @Bean
    public String tradingWorkerId() {
        return "trading-worker-" + UUID.randomUUID();
    }

    @Bean
    public Task<CycleScheduleData> tradingCycleTask(
            CycleExecutionOrchestrator orchestrator,
            String tradingWorkerId) {
        return Tasks.recurringWithPersistentSchedule(TRADING_CYCLE_TASK)
                .execute((inst, ctx) -> {
                    UUID instanceId = inst.getData().instanceId();
                    try {
                        orchestrator.runOnce(instanceId, tradingWorkerId);
                    } catch (RuntimeException ex) {
                        log.warn("trading cycle failed instance={} message={}",
                                instanceId, ex.getMessage());
                        throw ex;
                    }
                });
    }
}
