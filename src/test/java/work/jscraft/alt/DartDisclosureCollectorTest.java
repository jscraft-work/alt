package work.jscraft.alt;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.disclosure.DartDisclosureCollector;
import work.jscraft.alt.collector.application.disclosure.DartDisclosureCollector.CollectResult;
import work.jscraft.alt.disclosure.application.DisclosureExternalRecords.ExternalDisclosureItem;
import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemEntity;
import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemRepository;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;

import static org.assertj.core.api.Assertions.assertThat;

class DartDisclosureCollectorTest extends CollectorIntegrationTestSupport {

    @Autowired
    private DartDisclosureCollector dartDisclosureCollector;

    @Autowired
    private DisclosureItemRepository disclosureItemRepository;

    @Autowired
    private OpsEventRepository opsEventRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
        fakeDisclosureGateway.resetAll();
    }

    @Test
    void collectPersistsDisclosuresLinkedByDartCorpCodeAndDedupesOnSecondRun() {
        createAsset("005930", "삼성전자", "00126380");
        OffsetDateTime publishedAt = OffsetDateTime.of(2026, 5, 11, 1, 0, 0, 0, ZoneOffset.UTC);
        fakeDisclosureGateway.primeDisclosures("00126380", List.of(
                new ExternalDisclosureItem("00126380", "D-001", "주요사항보고서",
                        publishedAt, "내용 미리보기", "https://dart.fss.or.kr/001")));

        CollectResult result = dartDisclosureCollector.collect(
                "00126380", publishedAt.minusDays(1));

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);

        List<DisclosureItemEntity> rows = disclosureItemRepository.findAll();
        assertThat(rows).hasSize(1);
        DisclosureItemEntity row = rows.get(0);
        assertThat(row.getDartCorpCode()).isEqualTo("00126380");
        assertThat(row.getDisclosureNo()).isEqualTo("D-001");
        assertThat(row.getTitle()).isEqualTo("주요사항보고서");

        // 같은 disclosure_no 재호출 시 skip
        CollectResult second = dartDisclosureCollector.collect("00126380", publishedAt.minusDays(1));
        assertThat(second.saved()).isEqualTo(0);
        assertThat(second.skipped()).isEqualTo(1);
        assertThat(disclosureItemRepository.findAll()).hasSize(1);

        List<OpsEventEntity> events = opsEventRepository.findAll();
        assertThat(events.stream().map(OpsEventEntity::getServiceName))
                .containsOnly(MarketDataOpsEventRecorder.SERVICE_DISCLOSURE);
        assertThat(events.stream().map(OpsEventEntity::getStatusCode))
                .containsOnly(MarketDataOpsEventRecorder.STATUS_OK);
    }
}
