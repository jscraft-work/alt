package work.jscraft.alt.news.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.common.dto.ApiPagedResponse;
import work.jscraft.alt.common.dto.DateRangeDefaults;
import work.jscraft.alt.common.dto.PageRequestParams;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.news.application.NewsViews.NewsListItem;
import work.jscraft.alt.news.application.NewsViews.RelatedAssetView;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;

@Service
@Transactional(readOnly = true)
public class NewsQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NewsItemRepository newsItemRepository;
    private final NewsAssetRelationRepository newsAssetRelationRepository;
    private final AssetMasterRepository assetMasterRepository;
    private final StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;
    private final Clock clock;

    public NewsQueryService(
            NewsItemRepository newsItemRepository,
            NewsAssetRelationRepository newsAssetRelationRepository,
            AssetMasterRepository assetMasterRepository,
            StrategyInstanceWatchlistRelationRepository watchlistRelationRepository,
            Clock clock) {
        this.newsItemRepository = newsItemRepository;
        this.newsAssetRelationRepository = newsAssetRelationRepository;
        this.assetMasterRepository = assetMasterRepository;
        this.watchlistRelationRepository = watchlistRelationRepository;
        this.clock = clock;
    }

    public ApiPagedResponse<NewsListItem> listNews(
            String symbolCode,
            UUID strategyInstanceId,
            String usefulnessStatus,
            LocalDate dateFrom,
            LocalDate dateTo,
            String q,
            Integer page,
            Integer size) {
        OffsetDateTime fromInclusive = DateRangeDefaults.resolveFromInclusive(dateFrom, clock);
        OffsetDateTime toExclusive = DateRangeDefaults.resolveToExclusive(dateTo, clock);
        Pageable pageable = PageRequestParams.resolve(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));

        Set<UUID> assetFilter = resolveAssetFilter(symbolCode, strategyInstanceId);
        Set<UUID> newsItemIdFilter = null;
        if (assetFilter != null) {
            if (assetFilter.isEmpty()) {
                Page<NewsItemEntity> empty = Page.empty(pageable);
                return ApiPagedResponse.of(empty, List.of());
            }
            newsItemIdFilter = new HashSet<>();
            for (UUID assetId : assetFilter) {
                List<NewsAssetRelationEntity> relations = newsAssetRelationRepository.findByAssetMasterId(assetId);
                for (NewsAssetRelationEntity rel : relations) {
                    newsItemIdFilter.add(rel.getNewsItemId());
                }
            }
            if (newsItemIdFilter.isEmpty()) {
                Page<NewsItemEntity> empty = Page.empty(pageable);
                return ApiPagedResponse.of(empty, List.of());
            }
        }

        Set<UUID> finalNewsItemIdFilter = newsItemIdFilter;
        Specification<NewsItemEntity> spec = (root, query, cb) -> {
            Predicate predicate = cb.between(root.get("publishedAt"), fromInclusive, toExclusive);
            if (usefulnessStatus != null && !usefulnessStatus.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("usefulnessStatus"), usefulnessStatus));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("title")), pattern));
            }
            if (finalNewsItemIdFilter != null) {
                predicate = cb.and(predicate, root.get("id").in(finalNewsItemIdFilter));
            }
            return predicate;
        };

        Page<NewsItemEntity> result = newsItemRepository.findAll(spec, pageable);
        List<NewsItemEntity> items = result.getContent();

        Map<UUID, List<RelatedAssetView>> relatedByNews = loadRelatedAssets(items);

        List<NewsListItem> mapped = items.stream()
                .map(news -> new NewsListItem(
                        news.getId(),
                        news.getProviderName(),
                        news.getTitle(),
                        news.getArticleUrl(),
                        toKst(news.getPublishedAt()),
                        news.getSummary(),
                        news.getUsefulnessStatus(),
                        relatedByNews.getOrDefault(news.getId(), List.of())))
                .toList();
        return ApiPagedResponse.of(result, mapped);
    }

    private Set<UUID> resolveAssetFilter(String symbolCode, UUID strategyInstanceId) {
        Set<UUID> assetIds = null;
        if (symbolCode != null && !symbolCode.isBlank()) {
            Set<UUID> bySymbol = new HashSet<>();
            assetMasterRepository.findBySymbolCode(symbolCode)
                    .ifPresent(asset -> bySymbol.add(asset.getId()));
            assetIds = bySymbol;
        }
        if (strategyInstanceId != null) {
            Set<UUID> watchlistAssetIds = new HashSet<>();
            for (StrategyInstanceWatchlistRelationEntity relation
                    : watchlistRelationRepository.findByStrategyInstanceId(strategyInstanceId)) {
                watchlistAssetIds.add(relation.getAssetMaster().getId());
            }
            if (assetIds == null) {
                assetIds = watchlistAssetIds;
            } else {
                assetIds.retainAll(watchlistAssetIds);
            }
        }
        return assetIds;
    }

    private Map<UUID, List<RelatedAssetView>> loadRelatedAssets(List<NewsItemEntity> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<UUID> newsItemIds = items.stream().map(NewsItemEntity::getId).toList();
        List<NewsAssetRelationEntity> relations = newsAssetRelationRepository.findByNewsItemIdIn(newsItemIds);
        Set<UUID> assetMasterIds = new HashSet<>();
        for (NewsAssetRelationEntity rel : relations) {
            assetMasterIds.add(rel.getAssetMasterId());
        }
        Map<UUID, AssetMasterEntity> assetById = new HashMap<>();
        for (UUID assetId : assetMasterIds) {
            assetMasterRepository.findById(assetId).ifPresent(asset -> assetById.put(assetId, asset));
        }
        Map<UUID, List<RelatedAssetView>> grouped = new HashMap<>();
        for (NewsAssetRelationEntity rel : relations) {
            AssetMasterEntity asset = assetById.get(rel.getAssetMasterId());
            if (asset == null) {
                continue;
            }
            grouped.computeIfAbsent(rel.getNewsItemId(), key -> new java.util.ArrayList<>())
                    .add(new RelatedAssetView(asset.getSymbolCode(), asset.getSymbolName()));
        }
        return grouped;
    }

    private OffsetDateTime toKst(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(KST).toOffsetDateTime();
    }
}
