package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshot;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshotProvider;
import work.jscraft.alt.trading.application.inputspec.TradingInputAssembler;
import work.jscraft.alt.trading.application.inputspec.TradingInputAssembler.TradingInputAssembly;

import static org.assertj.core.api.Assertions.assertThat;

class TradingInputAssemblerTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private TradingInputAssembler assembler;

    @Autowired
    private SettingsSnapshotProvider snapshotProvider;

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @Autowired
    private PortfolioPositionRepository portfolioPositionRepository;

    @Autowired
    private MarketMinuteItemRepository marketMinuteItemRepository;

    @Autowired
    private NewsItemRepository newsItemRepository;

    @Autowired
    private NewsAssetRelationRepository newsAssetRelationRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
    }

    @Test
    void fullWatchlistModeReturnsAllWatchlistSymbols() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        AssetMasterEntity sk = createAsset("000660", "SK하이닉스", null);
        linkWatchlist(instance, samsung);
        linkWatchlist(instance, sk);
        // 보유는 005930만
        addPosition(instance, samsung.getSymbolCode(), new BigDecimal("12.00000000"));

        ObjectNode inputSpec = objectMapper.createObjectNode();
        inputSpec.put("scope", "full_watchlist");
        instance.setInputSpecOverrideJson(inputSpec);
        strategyInstanceRepository.saveAndFlush(instance);

        SettingsSnapshot snapshot = snapshotProvider.capture(instance.getId());
        TradingInputAssembly assembly = assembler.assembleDetailed(snapshot);

        assertThat(assembly.mode()).isEqualTo(TradingInputAssembler.MODE_FULL_WATCHLIST);
        assertThat(assembly.targetSymbols()).containsExactlyInAnyOrder("005930", "000660");
    }

    @Test
    void heldOnlyModeReturnsIntersectionOfWatchlistAndHeldPositions() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 B", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        AssetMasterEntity sk = createAsset("000660", "SK하이닉스", null);
        AssetMasterEntity kakao = createAsset("035720", "카카오", null);
        linkWatchlist(instance, samsung);
        linkWatchlist(instance, sk);
        // 보유 종목 중 005930만 워치리스트에 있고, kakao는 워치리스트에 없음
        addPosition(instance, samsung.getSymbolCode(), new BigDecimal("10.00000000"));
        addPosition(instance, kakao.getSymbolCode(), new BigDecimal("5.00000000"));

        ObjectNode inputSpec = objectMapper.createObjectNode();
        inputSpec.put("scope", "held_only");
        instance.setInputSpecOverrideJson(inputSpec);
        strategyInstanceRepository.saveAndFlush(instance);

        SettingsSnapshot snapshot = snapshotProvider.capture(instance.getId());
        TradingInputAssembly assembly = assembler.assembleDetailed(snapshot);

        assertThat(assembly.mode()).isEqualTo(TradingInputAssembler.MODE_HELD_ONLY);
        assertThat(assembly.targetSymbols()).containsExactly("005930");
    }

    @Test
    void assemblyGathersRecentBarsAndNewsForTargetSymbolsOnly() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 C", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", "00126380");
        AssetMasterEntity sk = createAsset("000660", "SK하이닉스", "00164779");
        linkWatchlist(instance, samsung);
        // sk는 워치리스트에 없음 → news/data 무시되어야 한다

        OffsetDateTime barTime = OffsetDateTime.of(2026, 5, 11, 0, 55, 0, 0, ZoneOffset.UTC);
        seedMinuteBar(samsung.getSymbolCode(), barTime);
        seedMinuteBar(sk.getSymbolCode(), barTime);

        seedNews("naver-1", "삼성전자 호재", samsung, OffsetDateTime.of(2026, 5, 11, 0, 50, 0, 0, ZoneOffset.UTC));
        seedNews("naver-2", "SK 뉴스", sk, OffsetDateTime.of(2026, 5, 11, 0, 50, 0, 0, ZoneOffset.UTC));

        ObjectNode inputSpec = objectMapper.createObjectNode();
        inputSpec.put("scope", "full_watchlist");
        instance.setInputSpecOverrideJson(inputSpec);
        strategyInstanceRepository.saveAndFlush(instance);

        SettingsSnapshot snapshot = snapshotProvider.capture(instance.getId());
        TradingInputAssembly assembly = assembler.assembleDetailed(snapshot);

        assertThat(assembly.targetSymbols()).containsExactly("005930");
        assertThat(assembly.minuteBars()).containsKey("005930");
        assertThat(assembly.minuteBars().get("005930")).hasSize(1);
        assertThat(assembly.minuteBars()).doesNotContainKey("000660");
        assertThat(assembly.news()).hasSize(1);
        assertThat(assembly.news().get(0).getTitle()).isEqualTo("삼성전자 호재");
    }

    private void linkWatchlist(StrategyInstanceEntity instance, AssetMasterEntity asset) {
        StrategyInstanceWatchlistRelationEntity relation = new StrategyInstanceWatchlistRelationEntity();
        relation.setStrategyInstance(instance);
        relation.setAssetMaster(asset);
        watchlistRelationRepository.saveAndFlush(relation);
    }

    private void addPosition(StrategyInstanceEntity instance, String symbolCode, BigDecimal qty) {
        PortfolioPositionEntity position = new PortfolioPositionEntity();
        position.setStrategyInstanceId(instance.getId());
        position.setSymbolCode(symbolCode);
        position.setQuantity(qty);
        position.setAvgBuyPrice(new BigDecimal("80000.00000000"));
        portfolioPositionRepository.saveAndFlush(position);
    }

    private void seedMinuteBar(String symbolCode, OffsetDateTime barTime) {
        MarketMinuteItemEntity bar = new MarketMinuteItemEntity();
        bar.setSymbolCode(symbolCode);
        bar.setBarTime(barTime);
        bar.setBusinessDate(barTime.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalDate());
        bar.setOpenPrice(new BigDecimal("80000.00000000"));
        bar.setHighPrice(new BigDecimal("80500.00000000"));
        bar.setLowPrice(new BigDecimal("79900.00000000"));
        bar.setClosePrice(new BigDecimal("80100.00000000"));
        bar.setVolume(new BigDecimal("123.0000"));
        bar.setSourceName("kis");
        marketMinuteItemRepository.saveAndFlush(bar);
    }

    private void seedNews(String externalId, String title, AssetMasterEntity asset, OffsetDateTime publishedAt) {
        NewsItemEntity news = new NewsItemEntity();
        news.setProviderName("naver");
        news.setExternalNewsId(externalId);
        news.setTitle(title);
        news.setArticleUrl("https://news.example/" + externalId);
        news.setPublishedAt(publishedAt);
        news.setSummary(title + " 요약");
        news.setUsefulnessStatus("useful");
        news = newsItemRepository.saveAndFlush(news);

        NewsAssetRelationEntity relation = new NewsAssetRelationEntity();
        relation.setNewsItemId(news.getId());
        relation.setAssetMasterId(asset.getId());
        newsAssetRelationRepository.saveAndFlush(relation);
    }

}
