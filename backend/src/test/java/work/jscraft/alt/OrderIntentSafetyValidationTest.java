package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Duration;
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
import work.jscraft.alt.trading.application.cycle.SettingsSnapshot;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshotProvider;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmRequest;
import work.jscraft.alt.trading.application.decision.OrderIntentSafetyValidator;
import work.jscraft.alt.trading.application.decision.ParsedDecision;
import work.jscraft.alt.trading.application.decision.ParsedDecision.ParsedOrder;
import work.jscraft.alt.trading.application.decision.TradeDecisionLogService;
import work.jscraft.alt.trading.application.decision.TradeOrderIntentGenerator;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;

import static org.assertj.core.api.Assertions.assertThat;

class OrderIntentSafetyValidationTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private TradeDecisionLogService tradeDecisionLogService;

    @Autowired
    private TradeCycleLifecycle lifecycle;

    @Autowired
    private SettingsSnapshotProvider snapshotProvider;

    @Autowired
    private TradeOrderIntentGenerator generator;

    @Autowired
    private OrderIntentSafetyValidator validator;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioPositionRepository portfolioPositionRepository;

    @Autowired
    private MarketPriceItemRepository marketPriceItemRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
    }

    @Test
    void blocksOrdersFailingQuantityPriceOrCashChecks() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        seedPortfolio(instance, new BigDecimal("100000.0000"));
        seedPosition(instance, "005930", new BigDecimal("10"));
        UUID cycleId = lifecycle.startCycle(instance);
        SettingsSnapshot snapshot = snapshotProvider.capture(instance.getId());

        ParsedDecision parsed = new ParsedDecision(
                "EXECUTE",
                "검증 케이스",
                new BigDecimal("0.5"),
                null,
                List.of(
                        // 1. 수량 0 → blocked
                        new ParsedOrder(1, "005930", "BUY", new BigDecimal("0"),
                                "MARKET", null, "qty zero", objectMapper.createArrayNode()),
                        // 2. LIMIT인데 가격 누락 → blocked
                        new ParsedOrder(2, "005930", "BUY", new BigDecimal("1"),
                                "LIMIT", null, "limit no price", objectMapper.createArrayNode()),
                        // 3. MARKET인데 가격 지정 → blocked
                        new ParsedOrder(3, "005930", "BUY", new BigDecimal("1"),
                                "MARKET", new BigDecimal("80000"), "market with price",
                                objectMapper.createArrayNode()),
                        // 4. 현금 부족 매수 → blocked (10주 * 80000 = 800000 > 100000)
                        new ParsedOrder(4, "005930", "BUY", new BigDecimal("10"),
                                "LIMIT", new BigDecimal("80000"), "no cash", objectMapper.createArrayNode()),
                        // 5. 보유 부족 매도 → blocked (보유 10주, 15주 매도)
                        new ParsedOrder(5, "005930", "SELL", new BigDecimal("15"),
                                "LIMIT", new BigDecimal("80000"), "no holdings", objectMapper.createArrayNode()),
                        // 6. 정상 매수 1주
                        new ParsedOrder(6, "005930", "BUY", new BigDecimal("1"),
                                "LIMIT", new BigDecimal("80000"), "ok", objectMapper.createArrayNode())));

        LlmRequest request = new LlmRequest(UUID.randomUUID(), "openclaw", "gpt-5.5",
                "prompt", Duration.ofSeconds(30));
        LlmCallResult result = LlmCallResult.success(0, "{...}", "");

        TradeDecisionLogEntity decisionLog = tradeDecisionLogService.saveSuccess(
                cycleId, parsed, snapshot, objectMapper.createObjectNode(), request, result, null);
        List<TradeOrderIntentEntity> intents = generator.generate(decisionLog, parsed);
        assertThat(intents).hasSize(6);

        validator.validate(instance.getId(), instance.getExecutionMode(), intents);

        assertThat(intents.get(0).getExecutionBlockedReason())
                .isEqualTo(OrderIntentSafetyValidator.REASON_QUANTITY_NON_POSITIVE);
        assertThat(intents.get(1).getExecutionBlockedReason())
                .isEqualTo(OrderIntentSafetyValidator.REASON_LIMIT_PRICE_MISSING);
        assertThat(intents.get(2).getExecutionBlockedReason())
                .isEqualTo(OrderIntentSafetyValidator.REASON_MARKET_PRICE_NOT_ALLOWED);
        assertThat(intents.get(3).getExecutionBlockedReason())
                .isEqualTo(OrderIntentSafetyValidator.REASON_INSUFFICIENT_CASH);
        assertThat(intents.get(4).getExecutionBlockedReason())
                .isEqualTo(OrderIntentSafetyValidator.REASON_INSUFFICIENT_POSITION);
        assertThat(intents.get(5).getExecutionBlockedReason()).isNull();
    }

    @Test
    void paperMarketBuyUsesLatestPriceForCashValidation() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 MARKET", "paper");
        seedPortfolio(instance, new BigDecimal("100000.0000"));
        seedPriceSnapshot("005930", new BigDecimal("80000.00000000"));
        UUID cycleId = lifecycle.startCycle(instance);
        SettingsSnapshot snapshot = snapshotProvider.capture(instance.getId());

        ParsedDecision parsed = new ParsedDecision(
                "EXECUTE",
                "마켓 검증",
                new BigDecimal("0.5"),
                null,
                List.of(new ParsedOrder(1, "005930", "BUY", new BigDecimal("2"),
                        "MARKET", null, "market no cash", objectMapper.createArrayNode())));

        LlmRequest request = new LlmRequest(UUID.randomUUID(), "openclaw", "gpt-5.5",
                "prompt", Duration.ofSeconds(30));
        LlmCallResult result = LlmCallResult.success(0, "{...}", "");

        TradeDecisionLogEntity decisionLog = tradeDecisionLogService.saveSuccess(
                cycleId, parsed, snapshot, objectMapper.createObjectNode(), request, result, null);
        List<TradeOrderIntentEntity> intents = generator.generate(decisionLog, parsed);

        validator.validate(instance.getId(), instance.getExecutionMode(), intents);

        assertThat(intents.get(0).getExecutionBlockedReason())
                .isEqualTo(OrderIntentSafetyValidator.REASON_INSUFFICIENT_CASH);
    }

    @Test
    void liveMarketBuyIsLeftToBrokerInsteadOfLocalCashValidation() {
        StrategyInstanceEntity instance = createActiveLiveInstance("KR 라이브 MARKET");
        seedPortfolio(instance, new BigDecimal("100000.0000"));
        UUID cycleId = lifecycle.startCycle(instance);
        SettingsSnapshot snapshot = snapshotProvider.capture(instance.getId());

        ParsedDecision parsed = new ParsedDecision(
                "EXECUTE",
                "라이브 마켓 검증",
                new BigDecimal("0.5"),
                null,
                List.of(new ParsedOrder(1, "005930", "BUY", new BigDecimal("2"),
                        "MARKET", null, "live market", objectMapper.createArrayNode())));

        LlmRequest request = new LlmRequest(UUID.randomUUID(), "openclaw", "gpt-5.5",
                "prompt", Duration.ofSeconds(30));
        LlmCallResult result = LlmCallResult.success(0, "{...}", "");

        TradeDecisionLogEntity decisionLog = tradeDecisionLogService.saveSuccess(
                cycleId, parsed, snapshot, objectMapper.createObjectNode(), request, result, null);
        List<TradeOrderIntentEntity> intents = generator.generate(decisionLog, parsed);

        validator.validate(instance.getId(), instance.getExecutionMode(), intents);

        assertThat(intents.get(0).getExecutionBlockedReason()).isNull();
    }

    private void seedPortfolio(StrategyInstanceEntity instance, BigDecimal cash) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(cash);
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);
    }

    private void seedPosition(StrategyInstanceEntity instance, String symbolCode, BigDecimal qty) {
        PortfolioPositionEntity p = new PortfolioPositionEntity();
        p.setStrategyInstanceId(instance.getId());
        p.setSymbolCode(symbolCode);
        p.setQuantity(qty);
        p.setAvgBuyPrice(new BigDecimal("80000.00000000"));
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
}
