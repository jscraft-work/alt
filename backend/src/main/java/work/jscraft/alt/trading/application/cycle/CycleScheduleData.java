package work.jscraft.alt.trading.application.cycle;

import java.io.Serializable;
import java.time.Duration;
import java.util.UUID;

import com.github.kagkarlsson.scheduler.task.helper.ScheduleAndData;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;

public final class CycleScheduleData implements ScheduleAndData {

    private static final long serialVersionUID = 1L;

    private UUID instanceId;
    private int cycleMinutes;
    private FixedDelay schedule;

    public CycleScheduleData() {
    }

    public CycleScheduleData(UUID instanceId, int cycleMinutes) {
        this.instanceId = instanceId;
        this.cycleMinutes = cycleMinutes;
        this.schedule = FixedDelay.of(Duration.ofMinutes(cycleMinutes));
    }

    @Override
    public Schedule getSchedule() {
        return schedule;
    }

    @Override
    public Serializable getData() {
        return instanceId;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public int cycleMinutes() {
        return cycleMinutes;
    }
}
