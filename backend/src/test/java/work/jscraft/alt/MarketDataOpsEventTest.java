package work.jscraft.alt;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.marketdata.KisPriceCollector;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDataOpsEventTest extends CollectorIntegrationTestSupport {

    @Autowired
    private KisPriceCollector kisPriceCollector;

    @Autowired
    private OpsEventRepository opsEventRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
        fakeMarketDataGateway.resetAll();
    }

    @Test
    void gatewayFailureWritesDownOpsEventAndRethrows() {
        fakeMarketDataGateway.primePriceFailure(
                "005930",
                new MarketDataException(
                        MarketDataException.Category.TIMEOUT,
                        "kis",
                        "현재가 조회 타임아웃"));

        assertThatThrownBy(() -> kisPriceCollector.collect("005930"))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("타임아웃");

        List<OpsEventEntity> events = opsEventRepository.findAll();
        assertThat(events).hasSize(1);
        OpsEventEntity event = events.get(0);
        assertThat(event.getServiceName()).isEqualTo(MarketDataOpsEventRecorder.SERVICE_MARKETDATA);
        assertThat(event.getStatusCode()).isEqualTo(MarketDataOpsEventRecorder.STATUS_DOWN);
        assertThat(event.getEventType()).isEqualTo(KisPriceCollector.EVENT_TYPE);
        assertThat(event.getMessage()).contains("타임아웃");
        assertThat(event.getPayloadJson()).isNotNull();
        assertThat(event.getPayloadJson().path("category").asText()).isEqualTo("TIMEOUT");
        assertThat(event.getPayloadJson().path("symbolCode").asText()).isEqualTo("005930");
    }
}
