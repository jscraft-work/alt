package work.jscraft.alt.common.dto;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class DateRangeDefaults {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final int DEFAULT_LOOKBACK_DAYS = 7;

    private DateRangeDefaults() {
    }

    public static OffsetDateTime resolveFromInclusive(LocalDate dateFrom, Clock clock) {
        LocalDate effective = dateFrom != null
                ? dateFrom
                : LocalDate.now(clock.withZone(KST)).minusDays(DEFAULT_LOOKBACK_DAYS - 1L);
        return effective.atStartOfDay(KST).toOffsetDateTime();
    }

    public static OffsetDateTime resolveToExclusive(LocalDate dateTo, Clock clock) {
        LocalDate effective = dateTo != null
                ? dateTo
                : LocalDate.now(clock.withZone(KST));
        return effective.plusDays(1L).atStartOfDay(KST).toOffsetDateTime();
    }
}
