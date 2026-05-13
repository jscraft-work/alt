package work.jscraft.alt;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.SchedulableInstance;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.trading.application.cycle.CycleScheduleData;
import work.jscraft.alt.trading.application.cycle.TradingCycleReconciler;
import work.jscraft.alt.trading.application.cycle.TradingCycleSchedulerConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradingCycleReconcilerTest {

    private SchedulerClient schedulerClient;
    private StrategyInstanceRepository repository;
    private TradingCycleReconciler reconciler;

    @BeforeEach
    void setUp() {
        schedulerClient = mock(SchedulerClient.class);
        repository = mock(StrategyInstanceRepository.class);
        reconciler = new TradingCycleReconciler(schedulerClient, repository);
    }

    @Test
    void activeInstanceWithoutSchedule_registersIfNotExists() throws Exception {
        StrategyInstanceEntity active = buildActiveInstance(5);
        when(repository.findByLifecycleStateAndAutoPausedReasonIsNull("active"))
                .thenReturn(List.of(active));
        when(schedulerClient.getScheduledExecutionsForTask(
                TradingCycleSchedulerConfig.TASK_NAME, CycleScheduleData.class))
                .thenReturn(List.of());

        reconciler.reconcile();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<SchedulableInstance<CycleScheduleData>> captor =
                ArgumentCaptor.forClass(SchedulableInstance.class);
        verify(schedulerClient).scheduleIfNotExists(captor.capture());

        CycleScheduleData scheduled = captor.getValue().getTaskInstance().getData();
        assertThat(scheduled.instanceId()).isEqualTo(active.getId());
        assertThat(scheduled.cycleMinutes()).isEqualTo(5);
        verify(schedulerClient, never()).cancel(any());
    }

    @Test
    void scheduledForInactiveInstance_cancelled() throws Exception {
        UUID staleId = UUID.randomUUID();
        when(repository.findByLifecycleStateAndAutoPausedReasonIsNull("active"))
                .thenReturn(List.of());

        @SuppressWarnings("unchecked")
        ScheduledExecution<CycleScheduleData> stale =
                (ScheduledExecution<CycleScheduleData>) mock(ScheduledExecution.class);
        when(stale.getData()).thenReturn(new CycleScheduleData(staleId, 5));
        when(schedulerClient.getScheduledExecutionsForTask(
                TradingCycleSchedulerConfig.TASK_NAME, CycleScheduleData.class))
                .thenReturn(List.of(stale));

        reconciler.reconcile();

        verify(schedulerClient).cancel(eq(
                TaskInstanceId.of(TradingCycleSchedulerConfig.TASK_NAME, staleId.toString())));
    }

    @Test
    void activeAndScheduled_doesNotCancel() throws Exception {
        StrategyInstanceEntity active = buildActiveInstance(5);
        when(repository.findByLifecycleStateAndAutoPausedReasonIsNull("active"))
                .thenReturn(List.of(active));

        @SuppressWarnings("unchecked")
        ScheduledExecution<CycleScheduleData> existing =
                (ScheduledExecution<CycleScheduleData>) mock(ScheduledExecution.class);
        when(existing.getData()).thenReturn(new CycleScheduleData(active.getId(), 5));
        when(schedulerClient.getScheduledExecutionsForTask(
                TradingCycleSchedulerConfig.TASK_NAME, CycleScheduleData.class))
                .thenReturn(List.of(existing));

        reconciler.reconcile();

        verify(schedulerClient).scheduleIfNotExists(any(SchedulableInstance.class));
        verify(schedulerClient, never()).cancel(any());
    }

    private StrategyInstanceEntity buildActiveInstance(int cycleMinutes) throws Exception {
        StrategyTemplateEntity template = new StrategyTemplateEntity();
        template.setDefaultCycleMinutes(cycleMinutes);

        StrategyInstanceEntity entity = new StrategyInstanceEntity();
        setId(entity, UUID.randomUUID());
        entity.setStrategyTemplate(template);
        return entity;
    }

    private void setId(Object entity, UUID id) throws Exception {
        Class<?> klass = entity.getClass();
        while (klass != null) {
            try {
                Field f = klass.getDeclaredField("id");
                f.setAccessible(true);
                f.set(entity, id);
                return;
            } catch (NoSuchFieldException ignored) {
                klass = klass.getSuperclass();
            }
        }
        throw new IllegalStateException("No 'id' field found on " + entity.getClass());
    }
}
