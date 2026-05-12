package work.jscraft.alt.trading.application.inputspec;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemEntity;
import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemRepository;
import work.jscraft.alt.macro.infrastructure.persistence.MacroItemEntity;
import work.jscraft.alt.macro.infrastructure.persistence.MacroItemRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshot;

@Component
public class TradingInputAssembler implements InputAssembler {

    public static final String MODE_HELD_ONLY = "held_only";
    public static final String MODE_FULL_WATCHLIST = "full_watchlist";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MINUTE_BAR_LOOKBACK_MINUTES = 60;
    private static final int NEWS_LOOKBACK_HOURS = 12;
    private static final int DISCLOSURE_LOOKBACK_HOURS = 24;

    private final PortfolioPositionRepository portfolioPositionRepository;
    private final MarketMinuteItemRepository marketMinuteItemRepository;
    private final NewsItemRepository newsItemRepository;
    private final NewsAssetRelationRepository newsAssetRelationRepository;
    private final DisclosureItemRepository disclosureItemRepository;
    private final MacroItemRepository macroItemRepository;
    private final AssetMasterRepository assetMasterRepository;
    private final Clock clock;

    public TradingInputAssembler(
            PortfolioPositionRepository portfolioPositionRepository,
            MarketMinuteItemRepository marketMinuteItemRepository,
            NewsItemRepository newsItemRepository,
            NewsAssetRelationRepository newsAssetRelationRepository,
            DisclosureItemRepository disclosureItemRepository,
            MacroItemRepository macroItemRepository,
            AssetMasterRepository assetMasterRepository,
            Clock clock) {
        this.portfolioPositionRepository = portfolioPositionRepository;
        this.marketMinuteItemRepository = marketMinuteItemRepository;
        this.newsItemRepository = newsItemRepository;
        this.newsAssetRelationRepository = newsAssetRelationRepository;
        this.disclosureItemRepository = disclosureItemRepository;
        this.macroItemRepository = macroItemRepository;
        this.assetMasterRepository = assetMasterRepository;
        this.clock = clock;
    }

    @Override
    public InputAssembly assemble(SettingsSnapshot snapshot) {
        TradingInputAssembly assembly = assembleDetailed(snapshot);
        return new InputAssembly(assembly.targetSymbols(), assembly.mode());
    }

    public TradingInputAssembly assembleDetailed(SettingsSnapshot snapshot) {
        String mode = resolveMode(snapshot.inputSpec());
        List<String> watchlist = snapshot.watchlistSymbols();
        List<String> heldSymbols = portfolioPositionRepository
                .findByStrategyInstanceId(snapshot.strategyInstanceId())
                .stream()
                .map(PortfolioPositionEntity::getSymbolCode)
                .toList();

        List<String> targetSymbols = resolveTargetSymbols(mode, watchlist, heldSymbols);

        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, List<MarketMinuteItemEntity>> bars = new LinkedHashMap<>();
        for (String symbolCode : targetSymbols) {
            bars.put(symbolCode, marketMinuteItemRepository
                    .findBySymbolCodeAndBarTimeBetweenOrderByBarTimeAsc(
                            symbolCode,
                            now.minusMinutes(MINUTE_BAR_LOOKBACK_MINUTES),
                            now));
        }

        Set<UUID> targetAssetIds = new HashSet<>();
        Map<String, String> dartCorpByTarget = new LinkedHashMap<>();
        for (String symbolCode : targetSymbols) {
            assetMasterRepository.findBySymbolCode(symbolCode).ifPresent(asset -> {
                targetAssetIds.add(asset.getId());
                if (asset.getDartCorpCode() != null) {
                    dartCorpByTarget.put(symbolCode, asset.getDartCorpCode());
                }
            });
        }

        List<NewsItemEntity> news = collectNews(targetAssetIds, now);
        List<DisclosureItemEntity> disclosures = collectDisclosures(dartCorpByTarget.values(), now);
        Optional<MacroItemEntity> macro = macroItemRepository.findByBaseDate(
                now.atZoneSameInstant(KST).toLocalDate());

        return new TradingInputAssembly(
                mode,
                targetSymbols,
                bars,
                news,
                disclosures,
                macro.orElse(null),
                now);
    }

    private String resolveMode(JsonNode inputSpec) {
        if (inputSpec != null && inputSpec.has("scope") && !inputSpec.get("scope").isNull()) {
            String scope = inputSpec.get("scope").asText();
            if (MODE_HELD_ONLY.equals(scope) || MODE_FULL_WATCHLIST.equals(scope)) {
                return scope;
            }
        }
        return MODE_FULL_WATCHLIST;
    }

    private List<String> resolveTargetSymbols(String mode, List<String> watchlist, List<String> heldSymbols) {
        if (MODE_HELD_ONLY.equals(mode)) {
            Set<String> watchSet = new HashSet<>(watchlist);
            List<String> intersection = new ArrayList<>();
            for (String held : heldSymbols) {
                if (watchSet.contains(held)) {
                    intersection.add(held);
                }
            }
            return List.copyOf(intersection);
        }
        return List.copyOf(watchlist);
    }

    private List<NewsItemEntity> collectNews(Set<UUID> targetAssetIds, OffsetDateTime now) {
        if (targetAssetIds.isEmpty()) {
            return List.of();
        }
        OffsetDateTime since = now.minusHours(NEWS_LOOKBACK_HOURS);
        Set<UUID> newsItemIds = new HashSet<>();
        for (UUID assetId : targetAssetIds) {
            for (NewsAssetRelationEntity relation : newsAssetRelationRepository.findByAssetMasterId(assetId)) {
                newsItemIds.add(relation.getNewsItemId());
            }
        }
        List<NewsItemEntity> filtered = new ArrayList<>();
        for (UUID newsItemId : newsItemIds) {
            newsItemRepository.findById(newsItemId)
                    .filter(item -> !item.getPublishedAt().isBefore(since))
                    .ifPresent(filtered::add);
        }
        filtered.sort((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()));
        return filtered;
    }

    private List<DisclosureItemEntity> collectDisclosures(
            java.util.Collection<String> dartCorpCodes,
            OffsetDateTime now) {
        if (dartCorpCodes.isEmpty()) {
            return List.of();
        }
        OffsetDateTime since = now.minusHours(DISCLOSURE_LOOKBACK_HOURS);
        Set<String> codeSet = new HashSet<>(dartCorpCodes);
        List<DisclosureItemEntity> matches = new ArrayList<>();
        for (DisclosureItemEntity disclosure : disclosureItemRepository.findAll()) {
            if (!codeSet.contains(disclosure.getDartCorpCode())) {
                continue;
            }
            if (disclosure.getPublishedAt().isBefore(since)) {
                continue;
            }
            matches.add(disclosure);
        }
        matches.sort((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()));
        return matches;
    }

    public record TradingInputAssembly(
            String mode,
            List<String> targetSymbols,
            Map<String, List<MarketMinuteItemEntity>> minuteBars,
            List<NewsItemEntity> news,
            List<DisclosureItemEntity> disclosures,
            MacroItemEntity macroSnapshot,
            OffsetDateTime assembledAt) {

        public long totalMinuteBars() {
            return minuteBars.values().stream().mapToLong(List::size).sum();
        }
    }
}
