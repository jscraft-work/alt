package work.jscraft.alt;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;

import work.jscraft.alt.integrations.telegram.TelegramAlertGateway;
import work.jscraft.alt.integrations.telegram.TelegramProperties;
import work.jscraft.alt.ops.application.AlertEvent;
import work.jscraft.alt.ops.application.AlertEvent.Severity;
import work.jscraft.alt.ops.application.AlertGateway.DispatchResult;
import work.jscraft.alt.ops.application.AlertPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramAlertPolicyTest {

    private final AlertPolicy policy = new AlertPolicy();

    @Test
    void disabledPolicyAlwaysSkips() {
        policy.setEnabled(false);
        OffsetDateTime now = at(2026, 5, 11, 10, 0);
        assertThat(policy.shouldDispatch(event(Severity.CRITICAL), now)).isFalse();
        assertThat(policy.shouldDispatch(event(Severity.WARNING), now)).isFalse();
    }

    @Test
    void quietHoursSkipNonCritical() {
        policy.setQuietHoursStart(LocalTime.of(22, 0));
        policy.setQuietHoursEnd(LocalTime.of(7, 0));
        // 새벽 03:00 KST
        OffsetDateTime quietTime = at(2026, 5, 11, 3, 0);
        assertThat(policy.shouldDispatch(event(Severity.WARNING), quietTime)).isFalse();
        assertThat(policy.shouldDispatch(event(Severity.INFO), quietTime)).isFalse();
    }

    @Test
    void criticalBypassesQuietHoursByDefault() {
        policy.setQuietHoursStart(LocalTime.of(22, 0));
        policy.setQuietHoursEnd(LocalTime.of(7, 0));
        OffsetDateTime quietTime = at(2026, 5, 11, 3, 0);
        assertThat(policy.shouldDispatch(event(Severity.CRITICAL), quietTime)).isTrue();
    }

    @Test
    void criticalDoesNotBypassWhenOverridden() {
        policy.setQuietHoursStart(LocalTime.of(22, 0));
        policy.setQuietHoursEnd(LocalTime.of(7, 0));
        policy.setCriticalBypassesQuietHours(false);
        OffsetDateTime quietTime = at(2026, 5, 11, 3, 0);
        assertThat(policy.shouldDispatch(event(Severity.CRITICAL), quietTime)).isFalse();
    }

    @Test
    void outsideQuietHoursDispatchesNormally() {
        policy.setQuietHoursStart(LocalTime.of(22, 0));
        policy.setQuietHoursEnd(LocalTime.of(7, 0));
        // 09:00 KST (한국 장 시작)
        OffsetDateTime daytime = at(2026, 5, 11, 9, 0);
        assertThat(policy.shouldDispatch(event(Severity.WARNING), daytime)).isTrue();
        assertThat(policy.shouldDispatch(event(Severity.INFO), daytime)).isTrue();
    }

    @Test
    void normalDayWindowSkipsInside() {
        policy.setQuietHoursStart(LocalTime.of(12, 0));
        policy.setQuietHoursEnd(LocalTime.of(14, 0));
        OffsetDateTime insideWindow = at(2026, 5, 11, 13, 0);
        OffsetDateTime outsideWindow = at(2026, 5, 11, 15, 0);
        assertThat(policy.shouldDispatch(event(Severity.WARNING), insideWindow)).isFalse();
        assertThat(policy.shouldDispatch(event(Severity.WARNING), outsideWindow)).isTrue();
    }

    @Test
    void gatewayReturnsAppropriateDispatchResult() {
        policy.setQuietHoursStart(LocalTime.of(22, 0));
        policy.setQuietHoursEnd(LocalTime.of(7, 0));
        Clock fixedQuiet = Clock.fixed(Instant.parse("2026-05-10T18:00:00Z"),
                ZoneId.of("Asia/Seoul")); // 03:00 KST
        TelegramProperties blankProps = new TelegramProperties();
        TelegramAlertGateway quietGateway = new TelegramAlertGateway(policy, fixedQuiet, blankProps);
        assertThat(quietGateway.dispatch(event(Severity.WARNING))).isEqualTo(DispatchResult.SKIPPED_QUIET_HOURS);
        // Credentials are blank in this policy-only test, so a send attempt returns FAILED rather than SENT.
        assertThat(quietGateway.dispatch(event(Severity.CRITICAL))).isEqualTo(DispatchResult.FAILED);

        AlertPolicy disabled = new AlertPolicy();
        disabled.setEnabled(false);
        TelegramAlertGateway disabledGateway = new TelegramAlertGateway(disabled, fixedQuiet, blankProps);
        assertThat(disabledGateway.dispatch(event(Severity.CRITICAL))).isEqualTo(DispatchResult.SKIPPED_DISABLED);

        Clock daytime = Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"),
                ZoneId.of("Asia/Seoul")); // 09:00 KST
        TelegramAlertGateway dayGateway = new TelegramAlertGateway(policy, daytime, blankProps);
        assertThat(dayGateway.dispatch(event(Severity.WARNING))).isEqualTo(DispatchResult.FAILED);
    }

    private AlertEvent event(Severity severity) {
        return new AlertEvent(severity, "test", "메시지", OffsetDateTime.now(), Map.of());
    }

    private OffsetDateTime at(int y, int m, int d, int hour, int minute) {
        return OffsetDateTime.of(y, m, d, hour, minute, 0, 0, java.time.ZoneOffset.ofHours(9));
    }
}
