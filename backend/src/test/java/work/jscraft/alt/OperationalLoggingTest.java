package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.common.security.BrokerAccountMasker;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.order.LiveOrderExecutor;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalLoggingTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private LiveOrderExecutor liveOrderExecutor;

    @Autowired
    private TradeCycleLifecycle lifecycle;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @Autowired
    private TradeDecisionLogRepository tradeDecisionLogRepository;

    @Autowired
    private TradeOrderIntentRepository tradeOrderIntentRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    private Logger liveExecutorLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeBrokerGateway.resetAll();

        liveExecutorLogger = (Logger) LoggerFactory.getLogger(LiveOrderExecutor.class);
        appender = new ListAppender<>();
        appender.start();
        liveExecutorLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        if (liveExecutorLogger != null && appender != null) {
            liveExecutorLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void brokerAccountMaskerHidesAllButLeadingDigits() {
        assertThat(BrokerAccountMasker.mask(null)).isNull();
        assertThat(BrokerAccountMasker.mask("12345678")).isEqualTo("1234****");
        assertThat(BrokerAccountMasker.mask("12345-67")).isEqualTo("1234***");
        assertThat(BrokerAccountMasker.mask("12")).isEqualTo("**");
    }

    @Test
    void liveOrderPlacementLogsMaskedBrokerAccountWithStructuredFields() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR LIVE LOG");
        seedCash(instance, new BigDecimal("10000000.0000"));
        TradeDecisionLogEntity decisionLog = seedDecisionLog(instance);
        TradeOrderIntentEntity intent = seedIntent(decisionLog, "005930", "BUY", "LIMIT",
                new BigDecimal("5"), new BigDecimal("81000"));

        fakeBrokerGateway.primePlaceResult(new PlaceOrderResult(
                "stub", "BROKER-001", PlaceOrderResult.STATUS_ACCEPTED,
                BigDecimal.ZERO, null, null));
        liveOrderExecutor.execute(instance, List.of(intent));

        List<ILoggingEvent> events = appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .filter(e -> e.getFormattedMessage().contains("live.order.place"))
                .toList();
        assertThat(events).hasSize(1);
        String message = events.get(0).getFormattedMessage();
        assertThat(message)
                .contains("instanceId=" + instance.getId())
                .contains("symbol=005930")
                .contains("side=BUY")
                .contains("qty=5")
                .contains("brokerAccount=")
                .doesNotContain("12345-67");
        // 마스킹된 4자리 + ***만 노출
        assertThat(message).contains("brokerAccount=1234***");
    }

    private void seedCash(StrategyInstanceEntity instance, BigDecimal cash) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(cash);
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);
    }

    private TradeDecisionLogEntity seedDecisionLog(StrategyInstanceEntity instance) {
        UUID cycleId = lifecycle.startCycle(instance);
        var cycleLog = tradeCycleLogRepository.findById(cycleId).orElseThrow();
        TradeDecisionLogEntity log = new TradeDecisionLogEntity();
        log.setTradeCycleLog(cycleLog);
        log.setStrategyInstance(instance);
        log.setCycleStartedAt(cycleLog.getCycleStartedAt());
        log.setCycleFinishedAt(cycleLog.getCycleStartedAt());
        log.setBusinessDate(cycleLog.getBusinessDate());
        log.setCycleStatus("EXECUTE");
        log.setSummary("log test");
        log.setSettingsSnapshotJson(objectMapper.createObjectNode());
        return tradeDecisionLogRepository.saveAndFlush(log);
    }

    private TradeOrderIntentEntity seedIntent(
            TradeDecisionLogEntity decisionLog,
            String symbol,
            String side,
            String orderType,
            BigDecimal qty,
            BigDecimal price) {
        TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
        intent.setTradeDecisionLog(decisionLog);
        intent.setSequenceNo(1);
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
