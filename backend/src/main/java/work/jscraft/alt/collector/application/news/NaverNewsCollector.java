package work.jscraft.alt.collector.application.news;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.news.application.NewsExternalRecords.ExternalNewsItem;
import work.jscraft.alt.news.application.NewsGateway;
import work.jscraft.alt.news.application.NewsGatewayException;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;

@Component
public class NaverNewsCollector {

    public static final String EVENT_TYPE = "news.collect";
    public static final String STATUS_UNCLASSIFIED = "unclassified";

    private final NewsGateway newsGateway;
    private final NewsItemRepository newsItemRepository;
    private final NewsAssetRelationRepository newsAssetRelationRepository;
    private final AssetMasterRepository assetMasterRepository;
    private final MarketDataOpsEventRecorder opsEventRecorder;

    public NaverNewsCollector(
            NewsGateway newsGateway,
            NewsItemRepository newsItemRepository,
            NewsAssetRelationRepository newsAssetRelationRepository,
            AssetMasterRepository assetMasterRepository,
            MarketDataOpsEventRecorder opsEventRecorder) {
        this.newsGateway = newsGateway;
        this.newsItemRepository = newsItemRepository;
        this.newsAssetRelationRepository = newsAssetRelationRepository;
        this.assetMasterRepository = assetMasterRepository;
        this.opsEventRecorder = opsEventRecorder;
    }

    public CollectResult collect(String symbolCode, OffsetDateTime sinceInclusive) {
        try {
            List<ExternalNewsItem> items = newsGateway.fetchNews(symbolCode, sinceInclusive);
            Optional<AssetMasterEntity> asset = assetMasterRepository.findBySymbolCode(symbolCode);
            int saved = 0;
            int skipped = 0;
            for (ExternalNewsItem item : items) {
                Optional<NewsItemEntity> existing = newsItemRepository
                        .findByProviderNameAndExternalNewsId(item.providerName(), item.externalNewsId());
                final NewsItemEntity newsItem;
                if (existing.isPresent()) {
                    newsItem = existing.get();
                    skipped++;
                } else {
                    NewsItemEntity created = new NewsItemEntity();
                    created.setProviderName(item.providerName());
                    created.setExternalNewsId(item.externalNewsId());
                    created.setTitle(item.title());
                    created.setArticleUrl(item.articleUrl());
                    created.setPublishedAt(item.publishedAt());
                    created.setSummary(item.summary());
                    created.setUsefulnessStatus(STATUS_UNCLASSIFIED);
                    newsItem = newsItemRepository.saveAndFlush(created);
                    saved++;
                }
                asset.ifPresent(value -> linkAsset(newsItem, value));
            }
            opsEventRecorder.recordSuccess(
                    MarketDataOpsEventRecorder.SERVICE_NEWS,
                    EVENT_TYPE,
                    symbolCode);
            return new CollectResult(saved, skipped);
        } catch (NewsGatewayException ex) {
            opsEventRecorder.recordFailure(
                    MarketDataOpsEventRecorder.SERVICE_NEWS,
                    EVENT_TYPE,
                    symbolCode,
                    ex.getMessage(),
                    ex.getCategory().name());
            throw ex;
        }
    }

    private void linkAsset(NewsItemEntity newsItem, AssetMasterEntity asset) {
        boolean exists = newsAssetRelationRepository.findByNewsItemIdIn(java.util.List.of(newsItem.getId()))
                .stream()
                .anyMatch(rel -> rel.getAssetMasterId().equals(asset.getId()));
        if (exists) {
            return;
        }
        NewsAssetRelationEntity relation = new NewsAssetRelationEntity();
        relation.setNewsItemId(newsItem.getId());
        relation.setAssetMasterId(asset.getId());
        newsAssetRelationRepository.saveAndFlush(relation);
    }

    public record CollectResult(int saved, int skipped) {
    }
}
