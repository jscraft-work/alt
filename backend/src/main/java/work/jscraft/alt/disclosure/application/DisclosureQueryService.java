package work.jscraft.alt.disclosure.application;

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
import work.jscraft.alt.disclosure.application.DisclosureViews.DisclosureListItem;
import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemEntity;
import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;

@Service
@Transactional(readOnly = true)
public class DisclosureQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DisclosureItemRepository disclosureItemRepository;
    private final AssetMasterRepository assetMasterRepository;
    private final StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;
    private final Clock clock;

    public DisclosureQueryService(
            DisclosureItemRepository disclosureItemRepository,
            AssetMasterRepository assetMasterRepository,
            StrategyInstanceWatchlistRelationRepository watchlistRelationRepository,
            Clock clock) {
        this.disclosureItemRepository = disclosureItemRepository;
        this.assetMasterRepository = assetMasterRepository;
        this.watchlistRelationRepository = watchlistRelationRepository;
        this.clock = clock;
    }

    public ApiPagedResponse<DisclosureListItem> listDisclosures(
            String symbolCode,
            String dartCorpCode,
            UUID strategyInstanceId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Integer page,
            Integer size) {
        OffsetDateTime fromInclusive = DateRangeDefaults.resolveFromInclusive(dateFrom, clock);
        OffsetDateTime toExclusive = DateRangeDefaults.resolveToExclusive(dateTo, clock);
        Pageable pageable = PageRequestParams.resolve(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));

        Set<String> dartCorpCodeFilter = resolveDartCorpCodes(symbolCode, dartCorpCode, strategyInstanceId);
        if (dartCorpCodeFilter != null && dartCorpCodeFilter.isEmpty()) {
            Page<DisclosureItemEntity> empty = Page.empty(pageable);
            return ApiPagedResponse.of(empty, List.of());
        }

        Specification<DisclosureItemEntity> spec = (root, query, cb) -> {
            Predicate predicate = cb.between(root.get("publishedAt"), fromInclusive, toExclusive);
            if (dartCorpCodeFilter != null) {
                predicate = cb.and(predicate, root.get("dartCorpCode").in(dartCorpCodeFilter));
            }
            return predicate;
        };

        Page<DisclosureItemEntity> result = disclosureItemRepository.findAll(spec, pageable);
        Map<String, AssetMasterEntity> assetByDartCorp = loadAssetsByDartCorpCode(result.getContent());

        List<DisclosureListItem> mapped = result.getContent().stream()
                .map(item -> toListItem(item, assetByDartCorp.get(item.getDartCorpCode())))
                .toList();
        return ApiPagedResponse.of(result, mapped);
    }

    private Set<String> resolveDartCorpCodes(String symbolCode, String dartCorpCode, UUID strategyInstanceId) {
        Set<String> dartCorpCodes = null;
        if (dartCorpCode != null && !dartCorpCode.isBlank()) {
            dartCorpCodes = new HashSet<>();
            dartCorpCodes.add(dartCorpCode);
        }
        if (symbolCode != null && !symbolCode.isBlank()) {
            Set<String> symbolDartCodes = new HashSet<>();
            assetMasterRepository.findBySymbolCode(symbolCode)
                    .filter(asset -> asset.getDartCorpCode() != null)
                    .ifPresent(asset -> symbolDartCodes.add(asset.getDartCorpCode()));
            if (dartCorpCodes == null) {
                dartCorpCodes = symbolDartCodes;
            } else {
                dartCorpCodes.retainAll(symbolDartCodes);
            }
        }
        if (strategyInstanceId != null) {
            Set<String> watchlistDartCodes = new HashSet<>();
            for (StrategyInstanceWatchlistRelationEntity relation
                    : watchlistRelationRepository.findByStrategyInstanceId(strategyInstanceId)) {
                AssetMasterEntity asset = relation.getAssetMaster();
                if (asset != null && asset.getDartCorpCode() != null) {
                    watchlistDartCodes.add(asset.getDartCorpCode());
                }
            }
            if (dartCorpCodes == null) {
                dartCorpCodes = watchlistDartCodes;
            } else {
                dartCorpCodes.retainAll(watchlistDartCodes);
            }
        }
        return dartCorpCodes;
    }

    private Map<String, AssetMasterEntity> loadAssetsByDartCorpCode(List<DisclosureItemEntity> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        Set<String> dartCorpCodes = new HashSet<>();
        for (DisclosureItemEntity item : items) {
            dartCorpCodes.add(item.getDartCorpCode());
        }
        Map<String, AssetMasterEntity> map = new HashMap<>();
        for (String code : dartCorpCodes) {
            assetMasterRepository.findByDartCorpCode(code).ifPresent(asset -> map.put(code, asset));
        }
        return map;
    }

    private DisclosureListItem toListItem(DisclosureItemEntity item, AssetMasterEntity asset) {
        return new DisclosureListItem(
                item.getId(),
                item.getDartCorpCode(),
                asset != null ? asset.getSymbolCode() : null,
                asset != null ? asset.getSymbolName() : null,
                item.getTitle(),
                toKst(item.getPublishedAt()),
                item.getPreviewText(),
                item.getDocumentUrl());
    }

    private OffsetDateTime toKst(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(KST).toOffsetDateTime();
    }
}
