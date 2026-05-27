package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketDailyItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketDailyItemRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator.CycleResult;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

import static org.assertj.core.api.Assertions.assertThat;

class PaperTradingCycleE2ETest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @Autowired
    private TradeDecisionLogRepository tradeDecisionLogRepository;

    @Autowired
    private TradeOrderIntentRepository tradeOrderIntentRepository;

    @Autowired
    private TradeOrderRepository tradeOrderRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioPositionRepository portfolioPositionRepository;

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @Autowired
    private MarketMinuteItemRepository marketMinuteItemRepository;

    @Autowired
    private MarketDailyItemRepository marketDailyItemRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void executeDecisionFlowsThroughOrderAndPortfolio() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        addWatchlist(instance, samsung);
        seedPortfolio(instance, new BigDecimal("5000000.0000"));

        fakeTradingDecisionEngine.primeSuccess("""
                {
                  "cycleStatus":"EXECUTE",
                  "summary":"삼성전자 5주 매수",
                  "confidence":0.71,
                  "orders":[
                    {"sequenceNo":1,"symbolCode":"005930","side":"BUY","quantity":5,"orderType":"LIMIT","price":80000,"rationale":"실적 기대"}
                  ]
                }
                """);

        CycleResult result = orchestrator.runOnce(instance.getId(), "test-worker");

        assertThat(result.status()).isEqualTo(CycleResult.Status.COMPLETED);
        assertThat(result.cycleStatus()).isEqualTo("EXECUTE");

        TradeCycleLogEntity cycleLog = tradeCycleLogRepository.findById(result.cycleLogId()).orElseThrow();
        assertThat(cycleLog.getCycleStage()).isEqualTo(TradeCycleLifecycle.STAGE_COMPLETED);
        assertThat(cycleLog.getCycleFinishedAt()).isNotNull();

        List<TradeDecisionLogEntity> decisionLogs = tradeDecisionLogRepository.findAll();
        assertThat(decisionLogs).hasSize(1);
        TradeDecisionLogEntity decisionLog = decisionLogs.get(0);
        assertThat(decisionLog.getCycleStatus()).isEqualTo("EXECUTE");
        assertThat(decisionLog.getSummary()).isEqualTo("삼성전자 5주 매수");

        List<TradeOrderIntentEntity> intents = tradeOrderIntentRepository
                .findByTradeDecisionLog_IdOrderBySequenceNoAsc(decisionLog.getId());
        assertThat(intents).hasSize(1);
        TradeOrderIntentEntity intent = intents.get(0);
        assertThat(intent.getExecutionBlockedReason()).isNull();

        List<TradeOrderEntity> orders = tradeOrderRepository.findAll();
        assertThat(orders).hasSize(1);
        TradeOrderEntity order = orders.get(0);
        assertThat(order.getOrderStatus()).isEqualTo("filled");
        assertThat(order.getAvgFilledPrice()).isEqualByComparingTo("80000");
        assertThat(order.getTradeOrderIntent().getId()).isEqualTo(intent.getId());
        assertThat(order.getPortfolioAfterJson()).isNotNull();

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        // cash = 5,000,000 - 5*80000 = 4,600,000
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("4600000.0000");
        assertThat(portfolioPositionRepository.findByStrategyInstanceIdAndSymbolCode(instance.getId(), "005930")
                .orElseThrow()
                .getQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void holdDecisionCompletesWithoutOrders() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 B", "paper");

        // default fake = HOLD
        CycleResult result = orchestrator.runOnce(instance.getId(), "test-worker");

        assertThat(result.status()).isEqualTo(CycleResult.Status.COMPLETED);
        assertThat(result.cycleStatus()).isEqualTo("HOLD");
        assertThat(tradeOrderIntentRepository.findAll()).isEmpty();
        assertThat(tradeOrderRepository.findAll()).isEmpty();

        TradeDecisionLogEntity decisionLog = tradeDecisionLogRepository.findAll().get(0);
        assertThat(decisionLog.getCycleStatus()).isEqualTo("HOLD");
    }

    @Test
    void injectsPreviousPositionMemoryIntoNextPrompt() {
        StrategyInstanceEntity instance = createActiveInstance("KR 박스 A", "paper");
        AssetMasterEntity kakao = createAsset("035720", "카카오", null);
        addWatchlist(instance, kakao);
        seedPortfolio(instance, new BigDecimal("1000000.0000"));
        seedBarData("035720", OffsetDateTime.parse("2026-05-11T10:00:00+09:00"));

        String prompt = """
                ---
                minute_bars: 60
                daily_bars: 30
                scope: full_watchlist
                ---
                <system>Test prompt.</system>
                {% for s in stocks %}
                <stock code="{{ s.code }}">
                  <position_memory>{{ s.position_memory }}</position_memory>
                </stock>
                {% endfor %}
                """;
        var promptVersion = addPromptVersion(instance, 2, prompt, "position memory");
        instance.setCurrentPromptVersion(promptVersion);
        strategyInstanceRepository.saveAndFlush(instance);

        fakeTradingDecisionEngine.primeSuccess("""
                {
                  "cycleStatus":"EXECUTE",
                  "summary":"카카오 매수",
                  "positionMemory":{
                    "buyReason":"박스 하단 반등 기대",
                    "expectedReactionDeadline":"2026-05-12T11:00:00+09:00",
                    "brokenIf":["박스 하단 이탈 후 회복 실패"],
                    "entryBoxLow":"41800",
                    "entryBoxHigh":"45200"
                  },
                  "orders":[
                    {"sequenceNo":1,"symbolCode":"035720","side":"BUY","quantity":20,"orderType":"LIMIT","price":42450,"rationale":"하단 반등 기대"}
                  ]
                }
                """);
        orchestrator.runOnce(instance.getId(), "test-worker");

        fakeTradingDecisionEngine.primeSuccess("""
                {
                  "cycleStatus":"HOLD",
                  "summary":"반등 대기",
                  "positionMemory":{
                    "buyReason":"박스 하단 반등 기대",
                    "expectedReactionDeadline":"2026-05-12T11:00:00+09:00",
                    "brokenIf":["박스 하단 이탈 후 회복 실패"],
                    "entryBoxLow":"41800",
                    "entryBoxHigh":"45200"
                  },
                  "orders":[]
                }
                """);
        orchestrator.runOnce(instance.getId(), "test-worker");

        assertThat(fakeTradingDecisionEngine.lastRequest()).isNotNull();
        assertThat(fakeTradingDecisionEngine.lastRequest().promptText())
                .contains("<position_memory>{\"buyReason\":\"박스 하단 반등 기대\"")
                .contains("\"expectedReactionDeadline\":\"2026-05-12T11:00:00+09:00\"");
    }

    @Test
    void logsSystemHoldWithoutCallingLlmWhenRequiredBarsAreMissing() {
        StrategyInstanceEntity instance = createActiveInstance("KR 박스 B", "paper");
        AssetMasterEntity kia = createAsset("000270", "기아", null);
        addWatchlist(instance, kia);
        seedPortfolio(instance, new BigDecimal("1000000.0000"));

        String prompt = """
                ---
                minute_bars: 60
                daily_bars: 30
                scope: full_watchlist
                ---
                <system>Test prompt.</system>
                {% for s in stocks %}
                <stock code="{{ s.code }}">{{ s.minute_bars }} {{ s.daily_bars }}</stock>
                {% endfor %}
                """;
        var promptVersion = addPromptVersion(instance, 2, prompt, "require bars");
        instance.setCurrentPromptVersion(promptVersion);
        strategyInstanceRepository.saveAndFlush(instance);

        CycleResult result = orchestrator.runOnce(instance.getId(), "test-worker");

        assertThat(result.status()).isEqualTo(CycleResult.Status.COMPLETED);
        assertThat(result.cycleStatus()).isEqualTo("HOLD");
        assertThat(fakeTradingDecisionEngine.lastRequest()).isNull();

        TradeDecisionLogEntity decisionLog = tradeDecisionLogRepository.findAll().get(0);
        assertThat(decisionLog.getCycleStatus()).isEqualTo("HOLD");
        assertThat(decisionLog.getSummary()).contains("LLM 판단 안함 - 데이터 부족");
        assertThat(decisionLog.getSummary()).contains("minute_bars 부족");
        assertThat(decisionLog.getSummary()).contains("daily_bars 부족");
        assertThat(decisionLog.getCallStatus()).isNull();
    }

    private void addWatchlist(StrategyInstanceEntity instance, AssetMasterEntity asset) {
        StrategyInstanceWatchlistRelationEntity relation = new StrategyInstanceWatchlistRelationEntity();
        relation.setStrategyInstance(instance);
        relation.setAssetMaster(asset);
        watchlistRelationRepository.saveAndFlush(relation);
    }

    private void seedPortfolio(StrategyInstanceEntity instance, BigDecimal cash) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(cash);
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);
    }

    private void seedBarData(String symbolCode, OffsetDateTime barTime) {
        MarketMinuteItemEntity minute = new MarketMinuteItemEntity();
        minute.setSymbolCode(symbolCode);
        minute.setBarTime(barTime);
        minute.setBusinessDate(barTime.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate());
        minute.setOpenPrice(new BigDecimal("42000.00000000"));
        minute.setHighPrice(new BigDecimal("42500.00000000"));
        minute.setLowPrice(new BigDecimal("41900.00000000"));
        minute.setClosePrice(new BigDecimal("42450.00000000"));
        minute.setVolume(new BigDecimal("1000.0000"));
        minute.setSourceName("test");
        marketMinuteItemRepository.saveAndFlush(minute);

        MarketDailyItemEntity daily = new MarketDailyItemEntity();
        daily.setSymbolCode(symbolCode);
        daily.setBusinessDate(LocalDate.of(2026, 5, 9));
        daily.setOpenPrice(new BigDecimal("43000.00000000"));
        daily.setHighPrice(new BigDecimal("45000.00000000"));
        daily.setLowPrice(new BigDecimal("41800.00000000"));
        daily.setClosePrice(new BigDecimal("42450.00000000"));
        daily.setVolume(new BigDecimal("100000.0000"));
        daily.setSourceName("test");
        marketDailyItemRepository.saveAndFlush(daily);
    }
}
