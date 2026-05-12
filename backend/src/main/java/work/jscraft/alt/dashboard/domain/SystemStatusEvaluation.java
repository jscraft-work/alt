package work.jscraft.alt.dashboard.domain;

import java.time.OffsetDateTime;

public record SystemStatusEvaluation(
        String serviceName,
        SystemStatusCode statusCode,
        String message,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime occurredAt) {
}
