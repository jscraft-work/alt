package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.order.PaperOrderExecutor;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;

import static org.assertj.core.api.Assertions.assertThat;

class PaperOrderExecutorTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private PaperOrderExecutor paperOrderExecutor;

    @Autowired
    private TradeCycleLifecycle lifecycle;

    @Autowired
    private TradeOrderIntentRepository tradeOrderIntentRepository;

    @Autowired
    private TradeDecisionLogRepository tradeDecisionLogRepository;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @Autowired
    private MarketPriceItemRepository marketPriceItemRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioPositionRepository portfolioPositionRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
    }

    @Test
    void limitBuyFillsAtIntentPriceAndUpdatesPortfolio() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        seedCash(instance, new BigDecimal("10000000.0000"));
        // walker 호가 — ask 1 = 81,000 (intent LIMIT 와 동일). avgFilled = 81,100 (한 틱 양보)
        seedOrderbook("005930",
                List.of(81_000L, 81_100L, 81_200L, 81_300L, 81_400L),
                List.of(100L, 100L, 100L, 100L, 100L),
                List.of(80_900L, 80_800L, 80_700L, 80_600L, 80_500L),
                List.of(100L, 100L, 100L, 100L, 100L));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, 1, "005930", "BUY", "LIMIT",
                new BigDecimal("5"), new BigDecimal("81000"));

        List<TradeOrderEntity> orders = paperOrderExecutor.execute(instance, List.of(intent));

        assertThat(orders).hasSize(1);
        TradeOrderEntity order = orders.get(0);
        assertThat(order.getOrderStatus()).isEqualTo("filled");
        assertThat(order.getExecutionMode()).isEqualTo("paper");
        assertThat(order.getRequestedPrice()).isEqualByComparingTo("81000");
        assertThat(order.getAvgFilledPrice()).isEqualByComparingTo("81100");
        assertThat(order.getFilledQuantity()).isEqualByComparingTo("5");
        // grossFill = 81,100 × 5 = 405,500. commission = 0.00014 × 405,500 = 56.77
        // paperActual = 405,500 + 56.77 = 405,556.77
        // cash = 10,000,000 - 405,556.77 = 9,594,443.23
        assertThat(order.getPortfolioAfterJson()).isNotNull();
        assertThat(order.getPortfolioAfterJson().path("cashAmount").decimalValue())
                .isEqualByComparingTo("9594443.2300");

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("9594443.2300");
        PortfolioPositionEntity position = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(instance.getId(), "005930").orElseThrow();
        assertThat(position.getQuantity()).isEqualByComparingTo("5");
        // 평단가 = avgFilledPrice (수수료 제외 base)
        assertThat(position.getAvgBuyPrice()).isEqualByComparingTo("81100");
    }

    @Test
    void marketBuyUsesLatestSnapshotPrice() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 B", "paper");
        seedCash(instance, new BigDecimal("10000000.0000"));
        // walker 호가 — MARKET 의 requested_price = walker.referencePrice (= ask 1 = 82,000)
        seedOrderbook("005930",
                List.of(82_000L, 82_100L, 82_200L, 82_300L, 82_400L),
                List.of(100L, 100L, 100L, 100L, 100L),
                List.of(81_900L, 81_800L, 81_700L, 81_600L, 81_500L),
                List.of(100L, 100L, 100L, 100L, 100L));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, 1, "005930", "BUY", "MARKET",
                new BigDecimal("3"), null);

        List<TradeOrderEntity> orders = paperOrderExecutor.execute(instance, List.of(intent));

        assertThat(orders).hasSize(1);
        TradeOrderEntity order = orders.get(0);
        assertThat(order.getAvgFilledPrice()).isEqualByComparingTo("82100");
        // MARKET → requested_price = walker.referencePrice = ask 1 = 82,000
        assertThat(order.getRequestedPrice()).isEqualByComparingTo("82000");

        // grossFill = 82,100 × 3 = 246,300. commission = 0.00014 × 246,300 = 34.482
        // paperActual = 246,300 + 34.482 = 246,334.4820
        // cash = 10,000,000 - 246,334.4820 = 9,753,665.5180
        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("9753665.5180");
    }

    @Test
    void limitSellRealizesPnlAndReducesPosition() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 C", "paper");
        seedCash(instance, new BigDecimal("1000000.0000"));
        seedPosition(instance, "005930", new BigDecimal("10"), new BigDecimal("80000"));
        // walker 호가 — SELL: bid 1 = 85,000 (LIMIT 와 동일). avgFilled = 84,900 (한 틱 양보)
        seedOrderbook("005930",
                List.of(85_100L, 85_200L, 85_300L, 85_400L, 85_500L),
                List.of(100L, 100L, 100L, 100L, 100L),
                List.of(85_000L, 84_900L, 84_800L, 84_700L, 84_600L),
                List.of(100L, 100L, 100L, 100L, 100L));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, 1, "005930", "SELL", "LIMIT",
                new BigDecimal("4"), new BigDecimal("85000"));

        paperOrderExecutor.execute(instance, List.of(intent));

        // grossFill = 84,900 × 4 = 339,600
        // sellTax = 0.0015 × 339,600 = 509.4000
        // commission = 0.00014 × 339,600 = 47.5440
        // paperActual = 339,600 - 509.4000 - 47.5440 = 339,043.0560
        // cash = 1,000,000 + 339,043.0560 = 1,339,043.0560
        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("1339043.0560");
        // realized = cashIn - costBasis = 339,043.0560 - 80,000 × 4 = 19,043.0560
        assertThat(portfolio.getRealizedPnlToday()).isEqualByComparingTo("19043.0560");

        PortfolioPositionEntity position = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(instance.getId(), "005930").orElseThrow();
        assertThat(position.getQuantity()).isEqualByComparingTo("6");
        assertThat(position.getAvgBuyPrice()).isEqualByComparingTo("80000");
    }

    @Test
    void marketSellUsesLatestSnapshotPrice() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 D", "paper");
        seedCash(instance, new BigDecimal("500000.0000"));
        seedPosition(instance, "005930", new BigDecimal("10"), new BigDecimal("80000"));
        // walker 호가 — bid 1 = 83,000. avgFilled = 82,900 (한 틱 양보)
        seedOrderbook("005930",
                List.of(83_100L, 83_200L, 83_300L, 83_400L, 83_500L),
                List.of(100L, 100L, 100L, 100L, 100L),
                List.of(83_000L, 82_900L, 82_800L, 82_700L, 82_600L),
                List.of(100L, 100L, 100L, 100L, 100L));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, 1, "005930", "SELL", "MARKET",
                new BigDecimal("2"), null);

        List<TradeOrderEntity> orders = paperOrderExecutor.execute(instance, List.of(intent));

        TradeOrderEntity order = orders.get(0);
        assertThat(order.getAvgFilledPrice()).isEqualByComparingTo("82900");
        // grossFill = 82,900 × 2 = 165,800
        // sellTax = 0.0015 × 165,800 = 248.7000
        // commission = 0.00014 × 165,800 = 23.2120
        // paperActual = 165,800 - 248.7000 - 23.2120 = 165,528.0880
        // realized = 165,528.0880 - 80,000 × 2 = 5,528.0880
        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getRealizedPnlToday()).isEqualByComparingTo("5528.0880");
    }

    @Test
    void blockedIntentsAreSkipped() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 E", "paper");
        seedCash(instance, new BigDecimal("10000.0000"));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity blocked = seedIntent(decisionLog, 1, "005930", "BUY", "LIMIT",
                new BigDecimal("100"), new BigDecimal("81000"));
        blocked.setExecutionBlockedReason("insufficient_cash");
        tradeOrderIntentRepository.saveAndFlush(blocked);

        List<TradeOrderEntity> orders = paperOrderExecutor.execute(instance, List.of(blocked));

        assertThat(orders).isEmpty();
    }

    private void seedCash(StrategyInstanceEntity instance, BigDecimal cash) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(cash);
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);
    }

    private void seedPosition(StrategyInstanceEntity instance, String symbolCode, BigDecimal qty, BigDecimal avg) {
        PortfolioPositionEntity p = new PortfolioPositionEntity();
        p.setStrategyInstanceId(instance.getId());
        p.setSymbolCode(symbolCode);
        p.setQuantity(qty);
        p.setAvgBuyPrice(avg);
        portfolioPositionRepository.saveAndFlush(p);
    }

    private void seedPriceSnapshot(String symbolCode, BigDecimal lastPrice) {
        MarketPriceItemEntity snapshot = new MarketPriceItemEntity();
        snapshot.setSymbolCode(symbolCode);
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 11, 0, 59, 0, 0, ZoneOffset.UTC);
        snapshot.setSnapshotAt(now);
        snapshot.setBusinessDate(now.toLocalDate());
        snapshot.setLastPrice(lastPrice);
        snapshot.setSourceName("kis");
        marketPriceItemRepository.saveAndFlush(snapshot);
    }

    private TradeDecisionLogEntity seedDecisionLog(StrategyInstanceEntity instance) {
        UUID cycleId = lifecycle.startCycle(instance);
        TradeCycleLogEntity cycleLog = tradeCycleLogRepository.findById(cycleId).orElseThrow();
        TradeDecisionLogEntity log = new TradeDecisionLogEntity();
        log.setTradeCycleLog(cycleLog);
        log.setStrategyInstance(instance);
        log.setCycleStartedAt(cycleLog.getCycleStartedAt());
        log.setCycleFinishedAt(cycleLog.getCycleStartedAt());
        log.setBusinessDate(cycleLog.getBusinessDate());
        log.setCycleStatus("EXECUTE");
        log.setSummary("test");
        log.setSettingsSnapshotJson(objectMapper.createObjectNode());
        return tradeDecisionLogRepository.saveAndFlush(log);
    }

    private TradeOrderIntentEntity seedIntent(
            TradeDecisionLogEntity decisionLog,
            int seq,
            String symbol,
            String side,
            String orderType,
            BigDecimal qty,
            BigDecimal price) {
        TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
        intent.setTradeDecisionLog(decisionLog);
        intent.setSequenceNo(seq);
        intent.setSymbolCode(symbol);
        intent.setSymbolName("Test Stock");
        intent.setSide(side);
        intent.setOrderType(orderType);
        intent.setQuantity(qty);
        intent.setPrice(price);
        intent.setRationale("test");
        intent.setEvidenceJson(objectMapper.createArrayNode());
        return tradeOrderIntentRepository.saveAndFlush(intent);
    }
}
