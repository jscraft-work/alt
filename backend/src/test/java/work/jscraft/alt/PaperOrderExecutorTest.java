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
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, 1, "005930", "BUY", "LIMIT",
                new BigDecimal("5"), new BigDecimal("81000"));

        List<TradeOrderEntity> orders = paperOrderExecutor.execute(instance, List.of(intent));

        assertThat(orders).hasSize(1);
        TradeOrderEntity order = orders.get(0);
        assertThat(order.getOrderStatus()).isEqualTo("filled");
        assertThat(order.getExecutionMode()).isEqualTo("paper");
        assertThat(order.getRequestedPrice()).isEqualByComparingTo("81000");
        assertThat(order.getAvgFilledPrice()).isEqualByComparingTo("81000");
        assertThat(order.getFilledQuantity()).isEqualByComparingTo("5");
        assertThat(order.getPortfolioAfterJson()).isNotNull();
        assertThat(order.getPortfolioAfterJson().path("cashAmount").decimalValue())
                .isEqualByComparingTo("9595000.0000");

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("9595000.0000");
        PortfolioPositionEntity position = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(instance.getId(), "005930").orElseThrow();
        assertThat(position.getQuantity()).isEqualByComparingTo("5");
        assertThat(position.getAvgBuyPrice()).isEqualByComparingTo("81000");
    }

    @Test
    void marketBuyUsesLatestSnapshotPrice() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 B", "paper");
        seedCash(instance, new BigDecimal("10000000.0000"));
        seedPriceSnapshot("005930", new BigDecimal("82000.00000000"));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, 1, "005930", "BUY", "MARKET",
                new BigDecimal("3"), null);

        List<TradeOrderEntity> orders = paperOrderExecutor.execute(instance, List.of(intent));

        assertThat(orders).hasSize(1);
        TradeOrderEntity order = orders.get(0);
        assertThat(order.getAvgFilledPrice()).isEqualByComparingTo("82000");
        assertThat(order.getRequestedPrice()).isNull();

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("9754000.0000");
    }

    @Test
    void limitSellRealizesPnlAndReducesPosition() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 C", "paper");
        seedCash(instance, new BigDecimal("1000000.0000"));
        seedPosition(instance, "005930", new BigDecimal("10"), new BigDecimal("80000"));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, 1, "005930", "SELL", "LIMIT",
                new BigDecimal("4"), new BigDecimal("85000"));

        paperOrderExecutor.execute(instance, List.of(intent));

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        // cash += 4 * 85000 = 340000
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("1340000.0000");
        // realized = (85000 - 80000) * 4 = 20000
        assertThat(portfolio.getRealizedPnlToday()).isEqualByComparingTo("20000.0000");

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
        seedPriceSnapshot("005930", new BigDecimal("83000.00000000"));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, 1, "005930", "SELL", "MARKET",
                new BigDecimal("2"), null);

        List<TradeOrderEntity> orders = paperOrderExecutor.execute(instance, List.of(intent));

        TradeOrderEntity order = orders.get(0);
        assertThat(order.getAvgFilledPrice()).isEqualByComparingTo("83000");
        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        assertThat(portfolio.getRealizedPnlToday()).isEqualByComparingTo("6000.0000");
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
