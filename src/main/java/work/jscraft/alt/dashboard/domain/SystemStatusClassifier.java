package work.jscraft.alt.dashboard.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class SystemStatusClassifier {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);
    private static final Duration DELAYED_THRESHOLD = Duration.ofMinutes(2);
    private static final Duration DOWN_THRESHOLD = Duration.ofMinutes(10);

    public SystemStatusEvaluation classify(ServiceHealthSnapshot snapshot, OffsetDateTime now) {
        OffsetDateTime lastSuccessAt = snapshot.lastSuccessAt();
        OffsetDateTime lastFailureAt = snapshot.lastFailureAt();

        if (lastSuccessAt == null || (lastFailureAt != null && !lastFailureAt.isBefore(lastSuccessAt))) {
            return new SystemStatusEvaluation(
                    snapshot.serviceName(),
                    SystemStatusCode.DOWN,
                    snapshot.message(),
                    lastSuccessAt,
                    pickOccurredAt(lastFailureAt, now));
        }

        if (snapshot.marketSensitive() && !isWithinMarketHours(now)) {
            return new SystemStatusEvaluation(
                    snapshot.serviceName(),
                    SystemStatusCode.OFF_HOURS,
                    null,
                    lastSuccessAt,
                    lastSuccessAt);
        }

        Duration sinceSuccess = Duration.between(lastSuccessAt, now);
        if (sinceSuccess.compareTo(DOWN_THRESHOLD) >= 0) {
            return new SystemStatusEvaluation(
                    snapshot.serviceName(),
                    SystemStatusCode.DOWN,
                    snapshot.message(),
                    lastSuccessAt,
                    pickOccurredAt(lastFailureAt, now));
        }
        if (sinceSuccess.compareTo(DELAYED_THRESHOLD) >= 0) {
            return new SystemStatusEvaluation(
                    snapshot.serviceName(),
                    SystemStatusCode.DELAYED,
                    snapshot.message(),
                    lastSuccessAt,
                    pickOccurredAt(lastFailureAt, lastSuccessAt));
        }
        return new SystemStatusEvaluation(
                snapshot.serviceName(),
                SystemStatusCode.OK,
                null,
                lastSuccessAt,
                lastSuccessAt);
    }

    private boolean isWithinMarketHours(OffsetDateTime now) {
        ZonedDateTime kst = now.atZoneSameInstant(KST);
        DayOfWeek dayOfWeek = kst.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = kst.toLocalTime();
        return !time.isBefore(MARKET_OPEN) && time.isBefore(MARKET_CLOSE);
    }

    private OffsetDateTime pickOccurredAt(OffsetDateTime preferred, OffsetDateTime fallback) {
        return preferred != null ? preferred : fallback;
    }
}
