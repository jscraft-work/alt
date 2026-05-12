package work.jscraft.alt.ops.application;

import java.time.OffsetDateTime;
import java.util.Map;

public record AlertEvent(
        Severity severity,
        String title,
        String message,
        OffsetDateTime occurredAt,
        Map<String, String> tags) {

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }
}
