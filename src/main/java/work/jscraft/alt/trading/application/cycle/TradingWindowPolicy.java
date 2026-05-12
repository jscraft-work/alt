package work.jscraft.alt.trading.application.cycle;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

@Component
public class TradingWindowPolicy {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
    public static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    public boolean isWithinWindow(OffsetDateTime now) {
        ZonedDateTime kst = now.atZoneSameInstant(KST);
        DayOfWeek dayOfWeek = kst.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = kst.toLocalTime();
        return !time.isBefore(MARKET_OPEN) && time.isBefore(MARKET_CLOSE);
    }
}
