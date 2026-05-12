package work.jscraft.alt;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.news.NewsAssessmentWorkflow;
import work.jscraft.alt.collector.application.news.NewsAssessmentWorkflow.AssessmentResult;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.news.application.NewsAssessmentEngine.UsefulnessStatus;
import work.jscraft.alt.news.application.NewsExternalRecords.ExternalArticleBody;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;

import static org.assertj.core.api.Assertions.assertThat;

class NewsAssessmentWorkflowTest extends CollectorIntegrationTestSupport {

    @Autowired
    private NewsAssessmentWorkflow newsAssessmentWorkflow;

    @Autowired
    private NewsItemRepository newsItemRepository;


    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
        fakeNewsGateway.resetAll();
        fakeNewsAssessmentEngine.resetAll();
    }

    @Test
    void assessmentUpdatesUsefulnessStatusFromEngineVerdict() {
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        fakeNewsAssessmentEngine.primeModelProfileId(modelProfile.getId());

        NewsItemEntity goodNews = persistNews("good-1", "삼성전자 호재 소식", "https://news.example/good");
        NewsItemEntity badNews = persistNews("bad-1", "오늘의 시황 정리", "https://news.example/bad");

        fakeNewsGateway.primeBody("https://news.example/good", new ExternalArticleBody("좋은 본문"));
        fakeNewsGateway.primeBody("https://news.example/bad", new ExternalArticleBody("그저그런 본문"));

        fakeNewsAssessmentEngine.primeVerdict("삼성전자 호재 소식", UsefulnessStatus.USEFUL);
        fakeNewsAssessmentEngine.primeVerdict("오늘의 시황 정리", UsefulnessStatus.NOT_USEFUL);

        AssessmentResult result = newsAssessmentWorkflow.assessUnclassified();

        assertThat(result.useful()).isEqualTo(1);
        assertThat(result.notUseful()).isEqualTo(1);

        NewsItemEntity reloadedGood = newsItemRepository.findById(goodNews.getId()).orElseThrow();
        assertThat(reloadedGood.getUsefulnessStatus()).isEqualTo("useful");
        assertThat(reloadedGood.getUsefulnessAssessedAt()).isNotNull();
        assertThat(reloadedGood.getUsefulnessModelProfileId()).isEqualTo(modelProfile.getId());

        NewsItemEntity reloadedBad = newsItemRepository.findById(badNews.getId()).orElseThrow();
        assertThat(reloadedBad.getUsefulnessStatus()).isEqualTo("not_useful");
        assertThat(reloadedBad.getUsefulnessAssessedAt()).isNotNull();
    }

    private NewsItemEntity persistNews(String externalId, String title, String url) {
        NewsItemEntity news = new NewsItemEntity();
        news.setProviderName("naver");
        news.setExternalNewsId(externalId);
        news.setTitle(title);
        news.setArticleUrl(url);
        news.setPublishedAt(OffsetDateTime.of(2026, 5, 11, 1, 0, 0, 0, ZoneOffset.UTC));
        news.setSummary(title + " 요약");
        news.setUsefulnessStatus("unclassified");
        return newsItemRepository.saveAndFlush(news);
    }
}
