package work.jscraft.alt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

class MutableClock extends Clock {

    private final ZoneId zoneId;
    private Instant instant;

    MutableClock(Instant instant, ZoneId zoneId) {
        this.instant = instant;
        this.zoneId = zoneId;
    }

    void setInstant(Instant instant) {
        this.instant = instant;
    }

    void advanceSeconds(long seconds) {
        this.instant = this.instant.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
        return zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
