package work.jscraft.alt.collector.application.news;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.news.application.NewsExternalRecords.ExternalArticleBody;
import work.jscraft.alt.news.application.NewsGateway;
import work.jscraft.alt.news.application.NewsGatewayException;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;

@Component
public class ArticleBodyFetcher {

    public static final String EVENT_TYPE = "news.article-body.fetch";

    private final NewsGateway newsGateway;
    private final MarketDataOpsEventRecorder opsEventRecorder;

    public ArticleBodyFetcher(NewsGateway newsGateway, MarketDataOpsEventRecorder opsEventRecorder) {
        this.newsGateway = newsGateway;
        this.opsEventRecorder = opsEventRecorder;
    }

    public Map<UUID, FetchResult> fetchBodies(List<NewsItemEntity> newsItems) {
        Map<UUID, FetchResult> results = new LinkedHashMap<>();
        for (NewsItemEntity newsItem : newsItems) {
            try {
                Optional<ExternalArticleBody> body = newsGateway.fetchArticleBody(newsItem.getArticleUrl());
                if (body.isEmpty()) {
                    results.put(newsItem.getId(), FetchResult.failure("empty-body"));
                    opsEventRecorder.recordFailure(
                            MarketDataOpsEventRecorder.SERVICE_NEWS,
                            EVENT_TYPE,
                            newsItem.getArticleUrl(),
                            "fetched body is empty",
                            "EMPTY_RESPONSE");
                    continue;
                }
                results.put(newsItem.getId(), FetchResult.success(body.get().bodyText()));
            } catch (NewsGatewayException ex) {
                results.put(newsItem.getId(), FetchResult.failure(ex.getMessage()));
                opsEventRecorder.recordFailure(
                        MarketDataOpsEventRecorder.SERVICE_NEWS,
                        EVENT_TYPE,
                        newsItem.getArticleUrl(),
                        ex.getMessage(),
                        ex.getCategory().name());
            }
        }
        if (newsItems.isEmpty()) {
            return new HashMap<>();
        }
        return results;
    }

    public record FetchResult(String bodyText, String failureReason) {

        public static FetchResult success(String bodyText) {
            return new FetchResult(bodyText, null);
        }

        public static FetchResult failure(String reason) {
            return new FetchResult(null, reason);
        }

        public boolean isSuccess() {
            return bodyText != null;
        }
    }
}
