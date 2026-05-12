package work.jscraft.alt;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.news.NaverNewsCollector;
import work.jscraft.alt.collector.application.news.NaverNewsCollector.CollectResult;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.news.application.NewsExternalRecords.ExternalNewsItem;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;

import static org.assertj.core.api.Assertions.assertThat;

class NaverNewsCollectorTest extends CollectorIntegrationTestSupport {

    @Autowired
    private NaverNewsCollector naverNewsCollector;

    @Autowired
    private NewsItemRepository newsItemRepository;

    @Autowired
    private NewsAssetRelationRepository newsAssetRelationRepository;

    @Autowired
    private OpsEventRepository opsEventRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
        fakeNewsGateway.resetAll();
    }

    @Test
    void collectPersistsNewsItemsAndLinksRelatedAsset() {
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", "00126380");
        OffsetDateTime publishedAt = OffsetDateTime.of(2026, 5, 11, 1, 0, 0, 0, ZoneOffset.UTC);
        fakeNewsGateway.primeNews("005930", List.of(
                new ExternalNewsItem("naver", "n-1", "삼성전자 실적 기대",
                        "https://news.example/1", publishedAt, "실적 기대..."),
                new ExternalNewsItem("naver", "n-2", "삼성전자 호재",
                        "https://news.example/2", publishedAt.plusMinutes(1), "호재 요약...")));

        CollectResult result = naverNewsCollector.collect("005930",
                OffsetDateTime.of(2026, 5, 10, 0, 0, 0, 0, ZoneOffset.UTC));

        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(0);

        List<NewsItemEntity> rows = newsItemRepository.findAll();
        assertThat(rows).hasSize(2);
        NewsItemEntity first = rows.stream()
                .filter(item -> "n-1".equals(item.getExternalNewsId()))
                .findFirst()
                .orElseThrow();
        assertThat(first.getProviderName()).isEqualTo("naver");
        assertThat(first.getTitle()).isEqualTo("삼성전자 실적 기대");
        assertThat(first.getUsefulnessStatus()).isEqualTo(NaverNewsCollector.STATUS_UNCLASSIFIED);

        List<UUID> newsIds = rows.stream().map(NewsItemEntity::getId).toList();
        List<NewsAssetRelationEntity> relations = newsAssetRelationRepository.findByNewsItemIdIn(newsIds);
        assertThat(relations).hasSize(2);
        assertThat(relations.stream().map(NewsAssetRelationEntity::getAssetMasterId))
                .containsOnly(samsung.getId());

        List<OpsEventEntity> events = opsEventRepository.findAll();
        assertThat(events).hasSize(1);
        OpsEventEntity event = events.get(0);
        assertThat(event.getServiceName()).isEqualTo(MarketDataOpsEventRecorder.SERVICE_NEWS);
        assertThat(event.getStatusCode()).isEqualTo(MarketDataOpsEventRecorder.STATUS_OK);
    }

    @Test
    void secondCollectSkipsDuplicateExternalIds() {
        createAsset("005930", "삼성전자", null);
        OffsetDateTime publishedAt = OffsetDateTime.of(2026, 5, 11, 1, 0, 0, 0, ZoneOffset.UTC);
        fakeNewsGateway.primeNews("005930", List.of(
                new ExternalNewsItem("naver", "n-1", "1",
                        "https://news.example/1", publishedAt, "s1")));

        naverNewsCollector.collect("005930", publishedAt.minusHours(1));
        CollectResult second = naverNewsCollector.collect("005930", publishedAt.minusHours(1));

        assertThat(second.saved()).isEqualTo(0);
        assertThat(second.skipped()).isEqualTo(1);
        assertThat(newsItemRepository.findAll()).hasSize(1);
    }
}
