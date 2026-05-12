package work.jscraft.alt.ops.application;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.alert")
public class AlertPolicy {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private boolean enabled = true;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
    private boolean criticalBypassesQuietHours = true;

    public boolean shouldDispatch(AlertEvent event, OffsetDateTime now) {
        if (!enabled) {
            return false;
        }
        if (event.severity() == AlertEvent.Severity.CRITICAL && criticalBypassesQuietHours) {
            return true;
        }
        if (quietHoursStart == null || quietHoursEnd == null) {
            return true;
        }
        LocalTime current = now.atZoneSameInstant(KST).toLocalTime();
        return !isWithinQuietHours(current);
    }

    private boolean isWithinQuietHours(LocalTime now) {
        if (quietHoursStart.equals(quietHoursEnd)) {
            return false;
        }
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            return !now.isBefore(quietHoursStart) && now.isBefore(quietHoursEnd);
        }
        // overnight window (e.g., 22:00 ~ 07:00)
        return !now.isBefore(quietHoursStart) || now.isBefore(quietHoursEnd);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalTime getQuietHoursStart() {
        return quietHoursStart;
    }

    public void setQuietHoursStart(LocalTime quietHoursStart) {
        this.quietHoursStart = quietHoursStart;
    }

    public LocalTime getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public void setQuietHoursEnd(LocalTime quietHoursEnd) {
        this.quietHoursEnd = quietHoursEnd;
    }

    public boolean isCriticalBypassesQuietHours() {
        return criticalBypassesQuietHours;
    }

    public void setCriticalBypassesQuietHours(boolean criticalBypassesQuietHours) {
        this.criticalBypassesQuietHours = criticalBypassesQuietHours;
    }
}
