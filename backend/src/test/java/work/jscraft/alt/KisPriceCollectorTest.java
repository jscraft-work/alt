package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.marketdata.KisPriceCollector;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.PriceSnapshot;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemRepository;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;

import static org.assertj.core.api.Assertions.assertThat;

class KisPriceCollectorTest extends CollectorIntegrationTestSupport {

    @Autowired
    private KisPriceCollector kisPriceCollector;

    @Autowired
    private MarketPriceItemRepository marketPriceItemRepository;

    @Autowired
    private OpsEventRepository opsEventRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
        fakeMarketDataGateway.resetAll();
    }

    @Test
    void collectPersistsPriceItemAndRecordsSuccessOpsEvent() {
        OffsetDateTime snapshotAt = OffsetDateTime.of(2026, 5, 11, 2, 0, 0, 0, ZoneOffset.UTC);
        fakeMarketDataGateway.primePrice("005930", new PriceSnapshot(
                "005930",
                snapshotAt,
                new BigDecimal("81000.00000000"),
                new BigDecimal("80500.00000000"),
                new BigDecimal("81500.00000000"),
                new BigDecimal("80300.00000000"),
                new BigDecimal("123456.0000"),
                "kis"));

        kisPriceCollector.collect("005930");

        List<MarketPriceItemEntity> rows = marketPriceItemRepository.findAll();
        assertThat(rows).hasSize(1);
        MarketPriceItemEntity row = rows.get(0);
        assertThat(row.getSymbolCode()).isEqualTo("005930");
        assertThat(row.getLastPrice()).isEqualByComparingTo("81000.00000000");
        assertThat(row.getSourceName()).isEqualTo("kis");
        assertThat(row.getBusinessDate()).isEqualTo("2026-05-11");

        List<OpsEventEntity> events = opsEventRepository.findAll();
        assertThat(events).hasSize(1);
        OpsEventEntity event = events.get(0);
        assertThat(event.getServiceName()).isEqualTo(MarketDataOpsEventRecorder.SERVICE_MARKETDATA);
        assertThat(event.getStatusCode()).isEqualTo(MarketDataOpsEventRecorder.STATUS_OK);
        assertThat(event.getEventType()).isEqualTo(KisPriceCollector.EVENT_TYPE);
    }
}
