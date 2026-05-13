package work.jscraft.alt.trading.application.inputspec;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemEntity;
import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemRepository;
import work.jscraft.alt.macro.infrastructure.persistence.MacroItemEntity;
import work.jscraft.alt.macro.infrastructure.persistence.MacroItemRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketFundamentalItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketFundamentalItemRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshot;

@Service
public class PromptContextAssembler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_KST = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter YMD_HM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String USEFULNESS_USEFUL = "USEFUL";

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository portfolioPositionRepository;
    private final MarketMinuteItemRepository marketMinuteItemRepository;
    private final MarketFundamentalItemRepository marketFundamentalItemRepository;
    private final NewsItemRepository newsItemRepository;
    private final NewsAssetRelationRepository newsAssetRelationRepository;
    private final DisclosureItemRepository disclosureItemRepository;
    private final MacroItemRepository macroItemRepository;
    private final AssetMasterRepository assetMasterRepository;
    private final StrategyInstanceRepository strategyInstanceRepository;
    private final Clock clock;

    public PromptContextAssembler(
            PortfolioRepository portfolioRepository,
            PortfolioPositionRepository portfolioPositionRepository,
            MarketMinuteItemRepository marketMinuteItemRepository,
            MarketFundamentalItemRepository marketFundamentalItemRepository,
            NewsItemRepository newsItemRepository,
            NewsAssetRelationRepository newsAssetRelationRepository,
            DisclosureItemRepository disclosureItemRepository,
            MacroItemRepository macroItemRepository,
            AssetMasterRepository assetMasterRepository,
            StrategyInstanceRepository strategyInstanceRepository,
            Clock clock) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioPositionRepository = portfolioPositionRepository;
        this.marketMinuteItemRepository = marketMinuteItemRepository;
        this.marketFundamentalItemRepository = marketFundamentalItemRepository;
        this.newsItemRepository = newsItemRepository;
        this.newsAssetRelationRepository = newsAssetRelationRepository;
        this.disclosureItemRepository = disclosureItemRepository;
        this.macroItemRepository = macroItemRepository;
        this.assetMasterRepository = assetMasterRepository;
        this.strategyInstanceRepository = strategyInstanceRepository;
        this.clock = clock;
    }

    public Map<String, Object> assemble(SettingsSnapshot snapshot, PromptInputSpec spec) {
        Map<String, Object> ctx = new LinkedHashMap<>();

        OffsetDateTime capturedAt = snapshot.capturedAt() != null
                ? snapshot.capturedAt()
                : OffsetDateTime.now(clock);
        ctx.put("current_time", capturedAt.atZoneSameInstant(KST).toOffsetDateTime().format(ISO_KST));

        List<PortfolioPositionEntity> heldPositions = portfolioPositionRepository
                .findByStrategyInstanceId(snapshot.strategyInstanceId());

        ctx.put("cash_amount", formatCashAmount(snapshot));
        ctx.put("held_positions", formatHeldPositions(heldPositions));

        List<String> targetSymbols = resolveTargetSymbols(snapshot, spec, heldPositions);
        ctx.put("stocks", buildStocks(targetSymbols, spec, capturedAt));

        if (spec.usesSource(PromptInputSpec.SourceSpec.Type.MACRO)) {
            ctx.put("macro", buildMacro(capturedAt));
        }

        return ctx;
    }

    private List<String> resolveTargetSymbols(
            SettingsSnapshot snapshot,
            PromptInputSpec spec,
            List<PortfolioPositionEntity> heldPositions) {
        List<String> watchlist = snapshot.watchlistSymbols() == null
                ? List.of() : snapshot.watchlistSymbols();
        if (spec.scope() == PromptInputSpec.Scope.HELD_ONLY) {
            Set<String> watchSet = new HashSet<>(watchlist);
            List<String> intersection = new ArrayList<>();
            for (PortfolioPositionEntity p : heldPositions) {
                if (watchSet.contains(p.getSymbolCode())) {
                    intersection.add(p.getSymbolCode());
                }
            }
            return List.copyOf(intersection);
        }
        return List.copyOf(watchlist);
    }

    private String formatCashAmount(SettingsSnapshot snapshot) {
        BigDecimal cash = portfolioRepository.findByStrategyInstanceId(snapshot.strategyInstanceId())
                .map(PortfolioEntity::getCashAmount)
                .orElseGet(() -> strategyInstanceRepository.findById(snapshot.strategyInstanceId())
                        .map(StrategyInstanceEntity::getBudgetAmount)
                        .orElse(BigDecimal.ZERO));
        return formatKrw(cash);
    }

    private String formatHeldPositions(List<PortfolioPositionEntity> positions) {
        if (positions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PortfolioPositionEntity p : positions) {
            String name = assetMasterRepository.findBySymbolCode(p.getSymbolCode())
                    .map(AssetMasterEntity::getSymbolName)
                    .orElse("");
            if (sb.length() > 0) sb.append('\n');
            sb.append("<position code=\"").append(p.getSymbolCode())
                    .append("\" name=\"").append(name)
                    .append("\" qty=").append(stripTrailingZeros(p.getQuantity()))
                    .append(" avg=").append(stripTrailingZeros(p.getAvgBuyPrice()))
                    .append("/>");
        }
        return sb.toString();
    }

    private List<Map<String, Object>> buildStocks(
            List<String> targetSymbols,
            PromptInputSpec spec,
            OffsetDateTime capturedAt) {
        List<Map<String, Object>> stocks = new ArrayList<>();
        for (String symbolCode : targetSymbols) {
            Optional<AssetMasterEntity> assetOpt = assetMasterRepository.findBySymbolCode(symbolCode);
            String name = assetOpt.map(AssetMasterEntity::getSymbolName).orElse("");

            String minuteBars = "";
            String fundamental = "";
            String news = "";
            String disclosures = "";
            String orderbook = "";

            for (PromptInputSpec.SourceSpec source : spec.sources()) {
                switch (source) {
                    case PromptInputSpec.SourceSpec.MinuteBar mb ->
                            minuteBars = buildMinuteBars(symbolCode, capturedAt, mb.lookbackMinutes());
                    case PromptInputSpec.SourceSpec.Fundamental f ->
                            fundamental = buildFundamental(symbolCode);
                    case PromptInputSpec.SourceSpec.News n ->
                            news = buildNews(assetOpt.map(AssetMasterEntity::getId).orElse(null),
                                    capturedAt, n.lookbackHours());
                    case PromptInputSpec.SourceSpec.Disclosure d ->
                            disclosures = buildDisclosures(
                                    assetOpt.map(AssetMasterEntity::getDartCorpCode).orElse(null),
                                    capturedAt, d.lookbackHours());
                    case PromptInputSpec.SourceSpec.Orderbook o -> {
                        // TODO KIS WS 활성화되면 채움 — v1 미구현
                    }
                    case PromptInputSpec.SourceSpec.Macro m -> { /* per-stock 아님 */ }
                }
            }

            stocks.add(StockContext.of(symbolCode, name, minuteBars, fundamental, news, disclosures, orderbook));
        }
        return stocks;
    }

    private String buildMinuteBars(String symbolCode, OffsetDateTime capturedAt, int lookbackMinutes) {
        OffsetDateTime from = capturedAt.minusMinutes(lookbackMinutes);
        List<MarketMinuteItemEntity> bars = marketMinuteItemRepository
                .findBySymbolCodeAndBarTimeBetweenOrderByBarTimeAsc(symbolCode, from, capturedAt);
        if (bars.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (MarketMinuteItemEntity b : bars) {
            if (sb.length() > 0) sb.append('\n');
            sb.append('[').append(b.getBarTime().atZoneSameInstant(KST).format(HHMM)).append(']')
                    .append(" o=").append(stripTrailingZeros(b.getOpenPrice()))
                    .append(" h=").append(stripTrailingZeros(b.getHighPrice()))
                    .append(" l=").append(stripTrailingZeros(b.getLowPrice()))
                    .append(" c=").append(stripTrailingZeros(b.getClosePrice()))
                    .append(" v=").append(stripTrailingZeros(b.getVolume()));
        }
        return sb.toString();
    }

    private String buildFundamental(String symbolCode) {
        Optional<MarketFundamentalItemEntity> opt =
                marketFundamentalItemRepository.findFirstBySymbolCodeOrderBySnapshotAtDesc(symbolCode);
        if (opt.isEmpty()) return "";
        MarketFundamentalItemEntity f = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("PER=").append(safe(f.getPer()))
                .append(" PBR=").append(safe(f.getPbr()))
                .append(" EPS=").append(safe(f.getEps()))
                .append(" BPS=").append(safe(f.getBps()))
                .append(" w52_hgpr=").append(safe(f.getW52Hgpr()))
                .append('(').append(safe(f.getW52HgprDate())).append(',')
                .append(safe(f.getW52HgprVrssPrprCtrt())).append("%)")
                .append(" w52_lwpr=").append(safe(f.getW52Lwpr()))
                .append('(').append(safe(f.getW52LwprDate())).append(',')
                .append(safe(f.getW52LwprVrssPrprCtrt())).append("%)")
                .append(" 외인=").append(safe(f.getHtsFrgnEhrt())).append('%')
                .append(" mrkt_warn=").append(safe(f.getMrktWarnClsCode()));
        return sb.toString();
    }

    private String buildNews(UUID assetId, OffsetDateTime capturedAt, int lookbackHours) {
        if (assetId == null) return "";
        OffsetDateTime since = capturedAt.minusHours(lookbackHours);
        List<NewsAssetRelationEntity> rels = newsAssetRelationRepository.findByAssetMasterId(assetId);
        if (rels.isEmpty()) return "";
        Set<UUID> ids = new HashSet<>();
        for (NewsAssetRelationEntity r : rels) ids.add(r.getNewsItemId());
        List<NewsItemEntity> items = newsItemRepository.findAllById(ids);
        List<NewsItemEntity> filtered = new ArrayList<>();
        for (NewsItemEntity n : items) {
            if (n.getPublishedAt() == null) continue;
            if (n.getPublishedAt().isBefore(since)) continue;
            if (!USEFULNESS_USEFUL.equals(n.getUsefulnessStatus())) continue;
            filtered.add(n);
        }
        filtered.sort((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()));
        if (filtered.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (NewsItemEntity n : filtered) {
            if (sb.length() > 0) sb.append('\n');
            sb.append('[').append(n.getPublishedAt().atZoneSameInstant(KST).format(YMD_HM)).append(']')
                    .append(' ').append(emptyIfNull(n.getTitle()));
            String summary = n.getSummary();
            if (summary != null && !summary.isBlank()) {
                sb.append(" — ").append(summary);
            }
        }
        return sb.toString();
    }

    private String buildDisclosures(String dartCorpCode, OffsetDateTime capturedAt, int lookbackHours) {
        if (dartCorpCode == null || dartCorpCode.isBlank()) return "";
        OffsetDateTime since = capturedAt.minusHours(lookbackHours);
        // TradingInputAssembler.collectDisclosures와 동일 패턴: findAll 후 코드/시점 필터.
        // 종목별 호출로 N+1이 되지만 v1은 종목 수가 작아 그대로 둠.
        List<DisclosureItemEntity> matches = new ArrayList<>();
        for (DisclosureItemEntity d : disclosureItemRepository.findAll()) {
            if (!dartCorpCode.equals(d.getDartCorpCode())) continue;
            if (d.getPublishedAt() == null || d.getPublishedAt().isBefore(since)) continue;
            matches.add(d);
        }
        matches.sort((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()));
        if (matches.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DisclosureItemEntity d : matches) {
            if (sb.length() > 0) sb.append('\n');
            sb.append('[').append(d.getPublishedAt().atZoneSameInstant(KST).format(YMD)).append(']')
                    .append(' ').append(emptyIfNull(d.getTitle()));
        }
        return sb.toString();
    }

    private String buildMacro(OffsetDateTime capturedAt) {
        Optional<MacroItemEntity> opt = macroItemRepository.findByBaseDate(
                capturedAt.atZoneSameInstant(KST).toLocalDate());
        if (opt.isEmpty()) return "";
        JsonNode payload = opt.get().getPayloadJson();
        if (payload == null || !payload.isObject()) return "";
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = payload.fieldNames();
        while (it.hasNext()) {
            String key = it.next();
            JsonNode v = payload.get(key);
            if (sb.length() > 0) sb.append('\n');
            sb.append(key).append('=').append(v == null || v.isNull() ? "" : v.asText());
        }
        return sb.toString();
    }

    private static String formatKrw(BigDecimal amount) {
        if (amount == null) return "0 KRW";
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0");
        return df.format(amount) + " KRW";
    }

    private static String stripTrailingZeros(BigDecimal v) {
        if (v == null) return "";
        return v.stripTrailingZeros().toPlainString();
    }

    private static String safe(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }
}
