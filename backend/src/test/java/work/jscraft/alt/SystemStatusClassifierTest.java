package work.jscraft.alt;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import work.jscraft.alt.dashboard.domain.ServiceHealthSnapshot;
import work.jscraft.alt.dashboard.domain.SystemStatusClassifier;
import work.jscraft.alt.dashboard.domain.SystemStatusCode;
import work.jscraft.alt.dashboard.domain.SystemStatusEvaluation;

import static org.assertj.core.api.Assertions.assertThat;

class SystemStatusClassifierTest {

    private final SystemStatusClassifier classifier = new SystemStatusClassifier();

    private static final OffsetDateTime MARKET_HOURS_NOW =
            OffsetDateTime.of(2026, 5, 11, 11, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final OffsetDateTime WEEKEND_NOW =
            OffsetDateTime.of(2026, 5, 9, 11, 0, 0, 0, ZoneOffset.ofHours(9));

    @Test
    void okWhenLastSuccessIsRecentAndDuringMarketHours() {
        OffsetDateTime lastSuccess = MARKET_HOURS_NOW.minusSeconds(30);
        ServiceHealthSnapshot snapshot = new ServiceHealthSnapshot("marketdata", true, lastSuccess, null, null);

        SystemStatusEvaluation evaluation = classifier.classify(snapshot, MARKET_HOURS_NOW);

        assertThat(evaluation.statusCode()).isEqualTo(SystemStatusCode.OK);
        assertThat(evaluation.lastSuccessAt()).isEqualTo(lastSuccess);
        assertThat(evaluation.message()).isNull();
    }

    @Test
    void delayedWhenLastSuccessOlderThanTwoMinutes() {
        OffsetDateTime lastSuccess = MARKET_HOURS_NOW.minusMinutes(3);
        ServiceHealthSnapshot snapshot = new ServiceHealthSnapshot("marketdata", true, lastSuccess, null, null);

        SystemStatusEvaluation evaluation = classifier.classify(snapshot, MARKET_HOURS_NOW);

        assertThat(evaluation.statusCode()).isEqualTo(SystemStatusCode.DELAYED);
        assertThat(evaluation.lastSuccessAt()).isEqualTo(lastSuccess);
    }

    @Test
    void downWhenLastSuccessOlderThanTenMinutes() {
        OffsetDateTime lastSuccess = MARKET_HOURS_NOW.minusMinutes(15);
        ServiceHealthSnapshot snapshot = new ServiceHealthSnapshot("marketdata", true, lastSuccess, null, "no data");

        SystemStatusEvaluation evaluation = classifier.classify(snapshot, MARKET_HOURS_NOW);

        assertThat(evaluation.statusCode()).isEqualTo(SystemStatusCode.DOWN);
        assertThat(evaluation.lastSuccessAt()).isEqualTo(lastSuccess);
        assertThat(evaluation.message()).isEqualTo("no data");
    }

    @Test
    void downWhenFailureMoreRecentThanSuccess() {
        OffsetDateTime lastSuccess = MARKET_HOURS_NOW.minusMinutes(5);
        OffsetDateTime lastFailure = MARKET_HOURS_NOW.minusMinutes(1);
        ServiceHealthSnapshot snapshot =
                new ServiceHealthSnapshot("marketdata", true, lastSuccess, lastFailure, "broker down");

        SystemStatusEvaluation evaluation = classifier.classify(snapshot, MARKET_HOURS_NOW);

        assertThat(evaluation.statusCode()).isEqualTo(SystemStatusCode.DOWN);
        assertThat(evaluation.lastSuccessAt()).isEqualTo(lastSuccess);
        assertThat(evaluation.occurredAt()).isEqualTo(lastFailure);
        assertThat(evaluation.message()).isEqualTo("broker down");
    }

    @Test
    void downWhenNoSuccessRecorded() {
        ServiceHealthSnapshot snapshot = new ServiceHealthSnapshot("marketdata", true, null, null, null);

        SystemStatusEvaluation evaluation = classifier.classify(snapshot, MARKET_HOURS_NOW);

        assertThat(evaluation.statusCode()).isEqualTo(SystemStatusCode.DOWN);
        assertThat(evaluation.lastSuccessAt()).isNull();
    }

    @Test
    void offHoursWhenMarketSensitiveAndOutsideMarketWindow() {
        OffsetDateTime lastSuccess = WEEKEND_NOW.minusMinutes(20);
        ServiceHealthSnapshot snapshot = new ServiceHealthSnapshot("marketdata", true, lastSuccess, null, null);

        SystemStatusEvaluation evaluation = classifier.classify(snapshot, WEEKEND_NOW);

        assertThat(evaluation.statusCode()).isEqualTo(SystemStatusCode.OFF_HOURS);
        assertThat(evaluation.lastSuccessAt()).isEqualTo(lastSuccess);
    }

    @Test
    void notMarketSensitiveServiceDoesNotGetOffHours() {
        OffsetDateTime lastSuccess = WEEKEND_NOW.minusSeconds(30);
        ServiceHealthSnapshot snapshot = new ServiceHealthSnapshot("news", false, lastSuccess, null, null);

        SystemStatusEvaluation evaluation = classifier.classify(snapshot, WEEKEND_NOW);

        assertThat(evaluation.statusCode()).isEqualTo(SystemStatusCode.OK);
    }
}
