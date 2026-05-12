package work.jscraft.alt.collector.application.news;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.news.ArticleBodyFetcher.FetchResult;
import work.jscraft.alt.news.application.NewsAssessmentEngine;
import work.jscraft.alt.news.application.NewsAssessmentEngine.AssessmentRequest;
import work.jscraft.alt.news.application.NewsAssessmentEngine.Verdict;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;

@Component
public class NewsAssessmentWorkflow {

    public static final String EVENT_TYPE = "news.usefulness.assess";

    private final NewsItemRepository newsItemRepository;
    private final ArticleBodyFetcher articleBodyFetcher;
    private final NewsAssessmentEngine assessmentEngine;
    private final MarketDataOpsEventRecorder opsEventRecorder;
    private final Clock clock;

    public NewsAssessmentWorkflow(
            NewsItemRepository newsItemRepository,
            ArticleBodyFetcher articleBodyFetcher,
            NewsAssessmentEngine assessmentEngine,
            MarketDataOpsEventRecorder opsEventRecorder,
            Clock clock) {
        this.newsItemRepository = newsItemRepository;
        this.articleBodyFetcher = articleBodyFetcher;
        this.assessmentEngine = assessmentEngine;
        this.opsEventRecorder = opsEventRecorder;
        this.clock = clock;
    }

    public AssessmentResult assessUnclassified() {
        List<NewsItemEntity> pending = newsItemRepository.findByUsefulnessStatus(
                NaverNewsCollector.STATUS_UNCLASSIFIED);
        Map<UUID, FetchResult> bodies = articleBodyFetcher.fetchBodies(pending);

        int useful = 0;
        int notUseful = 0;
        int skipped = 0;
        for (NewsItemEntity item : pending) {
            FetchResult fetch = bodies.get(item.getId());
            String bodyText = fetch != null && fetch.isSuccess() ? fetch.bodyText() : null;
            try {
                Verdict verdict = assessmentEngine.assess(
                        new AssessmentRequest(item.getTitle(), item.getSummary(), bodyText));
                item.setUsefulnessStatus(verdict.status().wireValue());
                item.setUsefulnessAssessedAt(OffsetDateTime.now(clock));
                item.setUsefulnessModelProfileId(verdict.modelProfileId());
                newsItemRepository.saveAndFlush(item);
                if (verdict.status() == NewsAssessmentEngine.UsefulnessStatus.USEFUL) {
                    useful++;
                } else if (verdict.status() == NewsAssessmentEngine.UsefulnessStatus.NOT_USEFUL) {
                    notUseful++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException ex) {
                skipped++;
                opsEventRecorder.recordFailure(
                        MarketDataOpsEventRecorder.SERVICE_NEWS,
                        EVENT_TYPE,
                        item.getArticleUrl(),
                        ex.getMessage(),
                        "ASSESSMENT_FAILED");
            }
        }
        opsEventRecorder.recordSuccess(MarketDataOpsEventRecorder.SERVICE_NEWS, EVENT_TYPE, null);
        return new AssessmentResult(useful, notUseful, skipped);
    }

    public record AssessmentResult(int useful, int notUseful, int skipped) {
    }
}
