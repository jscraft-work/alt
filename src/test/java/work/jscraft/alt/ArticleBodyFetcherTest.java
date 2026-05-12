package work.jscraft.alt;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.news.ArticleBodyFetcher;
import work.jscraft.alt.collector.application.news.ArticleBodyFetcher.FetchResult;
import work.jscraft.alt.news.application.NewsExternalRecords.ExternalArticleBody;
import work.jscraft.alt.news.application.NewsGatewayException;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleBodyFetcherTest extends CollectorIntegrationTestSupport {

    @Autowired
    private ArticleBodyFetcher articleBodyFetcher;

    @Autowired
    private NewsItemRepository newsItemRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
        fakeNewsGateway.resetAll();
    }

    @Test
    void perItemFetchFailureDoesNotDeleteOrFailOtherRows() {
        NewsItemEntity good = persistNews("good", "https://news.example/good");
        NewsItemEntity bad = persistNews("bad", "https://news.example/bad");

        fakeNewsGateway.primeBody("https://news.example/good", new ExternalArticleBody("좋은 본문"));
        fakeNewsGateway.primeBodyFailure(
                "https://news.example/bad",
                new NewsGatewayException(NewsGatewayException.Category.TRANSIENT, "naver", "본문 fetch 실패"));

        Map<UUID, FetchResult> results = articleBodyFetcher.fetchBodies(List.of(good, bad));

        assertThat(results).hasSize(2);
        assertThat(results.get(good.getId()).isSuccess()).isTrue();
        assertThat(results.get(good.getId()).bodyText()).isEqualTo("좋은 본문");
        assertThat(results.get(bad.getId()).isSuccess()).isFalse();
        assertThat(results.get(bad.getId()).failureReason()).contains("본문 fetch 실패");

        // 두 news_item 모두 그대로 남아있고 조회 가능
        assertThat(newsItemRepository.findAll()).hasSize(2);
        assertThat(newsItemRepository.findById(good.getId())).isPresent();
        assertThat(newsItemRepository.findById(bad.getId())).isPresent();
    }

    private NewsItemEntity persistNews(String externalId, String url) {
        NewsItemEntity news = new NewsItemEntity();
        news.setProviderName("naver");
        news.setExternalNewsId(externalId);
        news.setTitle(externalId + " 제목");
        news.setArticleUrl(url);
        news.setPublishedAt(OffsetDateTime.of(2026, 5, 11, 1, 0, 0, 0, ZoneOffset.UTC));
        news.setSummary(externalId + " 요약");
        news.setUsefulnessStatus("unclassified");
        return newsItemRepository.saveAndFlush(news);
    }
}
