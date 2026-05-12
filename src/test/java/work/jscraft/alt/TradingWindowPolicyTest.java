package work.jscraft.alt;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import work.jscraft.alt.trading.application.cycle.TradingWindowPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class TradingWindowPolicyTest {

    private final TradingWindowPolicy policy = new TradingWindowPolicy();

    @Test
    void kstMondayBeforeOpenIsOutOfWindow() {
        OffsetDateTime monday0859Kst = OffsetDateTime.of(2026, 5, 11, 8, 59, 0, 0, ZoneOffset.ofHours(9));
        assertThat(policy.isWithinWindow(monday0859Kst)).isFalse();
    }

    @Test
    void kstMondayAtOpenIsInWindow() {
        OffsetDateTime monday0900Kst = OffsetDateTime.of(2026, 5, 11, 9, 0, 0, 0, ZoneOffset.ofHours(9));
        assertThat(policy.isWithinWindow(monday0900Kst)).isTrue();
    }

    @Test
    void kstMondayBeforeCloseIsInWindow() {
        OffsetDateTime monday1529Kst = OffsetDateTime.of(2026, 5, 11, 15, 29, 0, 0, ZoneOffset.ofHours(9));
        assertThat(policy.isWithinWindow(monday1529Kst)).isTrue();
    }

    @Test
    void kstMondayAtCloseIsOutOfWindow() {
        OffsetDateTime monday1530Kst = OffsetDateTime.of(2026, 5, 11, 15, 30, 0, 0, ZoneOffset.ofHours(9));
        assertThat(policy.isWithinWindow(monday1530Kst)).isFalse();
    }

    @Test
    void saturdayIsOutOfWindow() {
        OffsetDateTime saturday1000Kst = OffsetDateTime.of(2026, 5, 9, 10, 0, 0, 0, ZoneOffset.ofHours(9));
        assertThat(policy.isWithinWindow(saturday1000Kst)).isFalse();
    }

    @Test
    void sundayIsOutOfWindow() {
        OffsetDateTime sunday1000Kst = OffsetDateTime.of(2026, 5, 10, 10, 0, 0, 0, ZoneOffset.ofHours(9));
        assertThat(policy.isWithinWindow(sunday1000Kst)).isFalse();
    }

    @Test
    void utcInputIsConvertedToKstBeforeWindowCheck() {
        // 2026-05-11 00:30:00Z = 2026-05-11 09:30 KST (within window)
        OffsetDateTime utc0030 = OffsetDateTime.of(2026, 5, 11, 0, 30, 0, 0, ZoneOffset.UTC);
        assertThat(policy.isWithinWindow(utc0030)).isTrue();

        // 2026-05-11 07:00:00Z = 2026-05-11 16:00 KST (out of window)
        OffsetDateTime utc0700 = OffsetDateTime.of(2026, 5, 11, 7, 0, 0, 0, ZoneOffset.UTC);
        assertThat(policy.isWithinWindow(utc0700)).isFalse();
    }
}
