package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshot;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshotProvider;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmRequest;
import work.jscraft.alt.trading.application.decision.ParsedDecision;
import work.jscraft.alt.trading.application.decision.ParsedDecision.ParsedOrder;
import work.jscraft.alt.trading.application.decision.TradeDecisionLogService;
import work.jscraft.alt.trading.application.decision.TradeOrderIntentGenerator;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;

import static org.assertj.core.api.Assertions.assertThat;

class TradeOrderIntentGenerationTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private TradeDecisionLogService tradeDecisionLogService;

    @Autowired
    private TradeCycleLifecycle lifecycle;

    @Autowired
    private SettingsSnapshotProvider snapshotProvider;

    @Autowired
    private TradeOrderIntentGenerator generator;

    @Autowired
    private TradeOrderIntentRepository tradeOrderIntentRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
    }

    @Test
    void ordersInParsedDecisionAreConvertedToIntents() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        UUID cycleId = lifecycle.startCycle(instance);
        SettingsSnapshot snapshot = snapshotProvider.capture(instance.getId());

        ArrayNode evidence = objectMapper.createArrayNode();
        ObjectNode evidenceItem = objectMapper.createObjectNode();
        evidenceItem.put("sourceType", "news");
        evidenceItem.put("title", "실적 호재");
        evidence.add(evidenceItem);

        ParsedDecision parsed = new ParsedDecision(
                "EXECUTE",
                "매수 2건",
                new BigDecimal("0.8"),
                null,
                List.of(
                        new ParsedOrder(1, "005930", "BUY", new BigDecimal("5"),
                                "LIMIT", new BigDecimal("81000"), "근거 1", evidence),
                        new ParsedOrder(2, "000660", "BUY", new BigDecimal("3"),
                                "MARKET", null, "근거 2", objectMapper.createArrayNode())));

        LlmRequest request = new LlmRequest(UUID.randomUUID(), "openclaw", "gpt-5.5",
                "the prompt", Duration.ofSeconds(30));
        LlmCallResult result = LlmCallResult.success(0, "{...}", "");
        TradeDecisionLogEntity decisionLog = tradeDecisionLogService.saveSuccess(
                cycleId, parsed, snapshot, objectMapper.createObjectNode(), request, result, null);

        List<TradeOrderIntentEntity> intents = generator.generate(decisionLog, parsed);

        assertThat(intents).hasSize(2);
        List<TradeOrderIntentEntity> reloaded = tradeOrderIntentRepository
                .findByTradeDecisionLog_IdOrderBySequenceNoAsc(decisionLog.getId());
        assertThat(reloaded).hasSize(2);
        TradeOrderIntentEntity first = reloaded.get(0);
        assertThat(first.getSequenceNo()).isEqualTo(1);
        assertThat(first.getSymbolCode()).isEqualTo("005930");
        assertThat(first.getSide()).isEqualTo("BUY");
        assertThat(first.getOrderType()).isEqualTo("LIMIT");
        assertThat(first.getPrice()).isEqualByComparingTo("81000");
        assertThat(first.getQuantity()).isEqualByComparingTo("5");
        assertThat(first.getRationale()).isEqualTo("근거 1");
        assertThat(first.getEvidenceJson().get(0).path("sourceType").asText()).isEqualTo("news");

        TradeOrderIntentEntity second = reloaded.get(1);
        assertThat(second.getOrderType()).isEqualTo("MARKET");
        assertThat(second.getPrice()).isNull();
    }
}
