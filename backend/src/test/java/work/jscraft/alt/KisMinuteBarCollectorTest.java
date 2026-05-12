package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.marketdata.KisMinuteBarCollector;
import work.jscraft.alt.collector.application.marketdata.KisMinuteBarCollector.CollectResult;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.MinuteBar;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemRepository;

import static org.assertj.core.api.Assertions.assertThat;

class KisMinuteBarCollectorTest extends CollectorIntegrationTestSupport {

    @Autowired
    private KisMinuteBarCollector kisMinuteBarCollector;

    @Autowired
    private MarketMinuteItemRepository marketMinuteItemRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
        fakeMarketDataGateway.resetAll();
    }

    @Test
    void collectInsertsBarsAndSkipsDuplicatesOnSecondRun() {
        LocalDate businessDate = LocalDate.of(2026, 5, 11);
        List<MinuteBar> initial = List.of(
                bar("005930", OffsetDateTime.of(2026, 5, 11, 0, 1, 0, 0, ZoneOffset.UTC)),
                bar("005930", OffsetDateTime.of(2026, 5, 11, 0, 2, 0, 0, ZoneOffset.UTC)),
                bar("005930", OffsetDateTime.of(2026, 5, 11, 0, 3, 0, 0, ZoneOffset.UTC)));
        fakeMarketDataGateway.primeMinuteBars("005930", initial);

        CollectResult firstResult = kisMinuteBarCollector.collect("005930", businessDate);

        assertThat(firstResult.saved()).isEqualTo(3);
        assertThat(firstResult.skipped()).isEqualTo(0);
        assertThat(marketMinuteItemRepository.findAll()).hasSize(3);

        List<MinuteBar> secondBatch = List.of(
                bar("005930", OffsetDateTime.of(2026, 5, 11, 0, 1, 0, 0, ZoneOffset.UTC)),
                bar("005930", OffsetDateTime.of(2026, 5, 11, 0, 2, 0, 0, ZoneOffset.UTC)),
                bar("005930", OffsetDateTime.of(2026, 5, 11, 0, 3, 0, 0, ZoneOffset.UTC)),
                bar("005930", OffsetDateTime.of(2026, 5, 11, 0, 4, 0, 0, ZoneOffset.UTC)));
        fakeMarketDataGateway.primeMinuteBars("005930", secondBatch);

        CollectResult secondResult = kisMinuteBarCollector.collect("005930", businessDate);

        assertThat(secondResult.saved()).isEqualTo(1);
        assertThat(secondResult.skipped()).isEqualTo(3);
        List<MarketMinuteItemEntity> rows = marketMinuteItemRepository.findAll();
        assertThat(rows).hasSize(4);
        assertThat(rows.stream().map(MarketMinuteItemEntity::getBarTime).toList())
                .contains(
                        OffsetDateTime.of(2026, 5, 11, 0, 1, 0, 0, ZoneOffset.UTC),
                        OffsetDateTime.of(2026, 5, 11, 0, 2, 0, 0, ZoneOffset.UTC),
                        OffsetDateTime.of(2026, 5, 11, 0, 3, 0, 0, ZoneOffset.UTC),
                        OffsetDateTime.of(2026, 5, 11, 0, 4, 0, 0, ZoneOffset.UTC));
    }

    private MinuteBar bar(String symbolCode, OffsetDateTime barTime) {
        return new MinuteBar(
                symbolCode,
                barTime,
                new BigDecimal("80000.00000000"),
                new BigDecimal("80500.00000000"),
                new BigDecimal("79900.00000000"),
                new BigDecimal("80100.00000000"),
                new BigDecimal("123.0000"),
                "kis");
    }
}
