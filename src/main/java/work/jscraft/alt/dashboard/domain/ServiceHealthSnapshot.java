package work.jscraft.alt.dashboard.domain;

import java.time.OffsetDateTime;

public record ServiceHealthSnapshot(
        String serviceName,
        boolean marketSensitive,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastFailureAt,
        String message) {
}
