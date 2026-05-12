package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChartApiTest extends AdminCatalogApiIntegrationTestSupport {

    @Autowired
    private MarketMinuteItemRepository marketMinuteItemRepository;

    @Autowired
    private TradeCycleLogRepository tradeCycleLogRepository;

    @Autowired
    private TradeDecisionLogRepository tradeDecisionLogRepository;

    @Autowired
    private TradeOrderIntentRepository tradeOrderIntentRepository;

    @Autowired
    private TradeOrderRepository tradeOrderRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
    }

    @Test
    void minutesReturnsBarsForRequestedDateOnly() throws Exception {
        seedMinuteBar("005930", OffsetDateTime.of(2026, 5, 11, 0, 1, 0, 0, ZoneOffset.UTC),
                "81000", "81100", "80900", "81050", "12031");
        seedMinuteBar("005930", OffsetDateTime.of(2026, 5, 11, 0, 2, 0, 0, ZoneOffset.UTC),
                "81050", "81200", "81000", "81150", "8400");
        // 다른 날짜는 제외
        seedMinuteBar("005930", OffsetDateTime.of(2026, 5, 10, 0, 1, 0, 0, ZoneOffset.UTC),
                "80900", "81000", "80800", "80950", "5000");

        mockMvc.perform(get("/api/charts/minutes")
                        .param("symbolCode", "005930")
                        .param("date", "2026-05-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbolCode").value("005930"))
                .andExpect(jsonPath("$.data.date").value("2026-05-11"))
                .andExpect(jsonPath("$.data.bars.length()").value(2))
                .andExpect(jsonPath("$.data.bars[0].barTime").value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data.bars[0].openPrice").value(81000))
                .andExpect(jsonPath("$.data.bars[0].highPrice").value(81100))
                .andExpect(jsonPath("$.data.bars[0].lowPrice").value(80900))
                .andExpect(jsonPath("$.data.bars[0].closePrice").value(81050));
    }

    @Test
    void orderOverlaysReturnsOrderMarkersForSymbolAndDate() throws Exception {
        StrategyInstanceEntity instance = createInstance("KR 모멘텀 A");

        seedOrder(instance, "005930", "BUY", "filled",
                OffsetDateTime.of(2026, 5, 11, 0, 35, 0, 0, ZoneOffset.UTC));
        // 다른 종목은 제외
        seedOrder(instance, "000660", "BUY", "filled",
                OffsetDateTime.of(2026, 5, 11, 0, 40, 0, 0, ZoneOffset.UTC));
        // 다른 날짜는 제외
        seedOrder(instance, "005930", "BUY", "filled",
                OffsetDateTime.of(2026, 5, 10, 0, 35, 0, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/charts/order-overlays")
                        .param("symbolCode", "005930")
                        .param("date", "2026-05-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].side").value("BUY"))
                .andExpect(jsonPath("$.data[0].orderStatus").value("filled"))
                .andExpect(jsonPath("$.data[0].requestedAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data[0].requestedQuantity").value(5));
    }

    @Test
    void orderOverlaysFiltersByStrategyInstanceWhenProvided() throws Exception {
        StrategyInstanceEntity instanceA = createInstance("KR 모멘텀 A");
        StrategyInstanceEntity instanceB = createInstance("KR 모멘텀 B");

        seedOrder(instanceA, "005930", "BUY", "filled",
                OffsetDateTime.of(2026, 5, 11, 0, 35, 0, 0, ZoneOffset.UTC));
        seedOrder(instanceB, "005930", "SELL", "filled",
                OffsetDateTime.of(2026, 5, 11, 0, 36, 0, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/charts/order-overlays")
                        .param("symbolCode", "005930")
                        .param("date", "2026-05-11")
                        .param("strategyInstanceId", instanceA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].side").value("BUY"));
    }

    private void seedMinuteBar(
            String symbolCode,
            OffsetDateTime barTime,
            String open,
            String high,
            String low,
            String close,
            String volume) {
        MarketMinuteItemEntity bar = new MarketMinuteItemEntity();
        bar.setSymbolCode(symbolCode);
        bar.setBarTime(barTime);
        bar.setBusinessDate(barTime.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalDate());
        bar.setOpenPrice(new BigDecimal(open).setScale(8));
        bar.setHighPrice(new BigDecimal(high).setScale(8));
        bar.setLowPrice(new BigDecimal(low).setScale(8));
        bar.setClosePrice(new BigDecimal(close).setScale(8));
        bar.setVolume(new BigDecimal(volume).setScale(4));
        bar.setSourceName("kis");
        marketMinuteItemRepository.saveAndFlush(bar);
    }

    private void seedOrder(
            StrategyInstanceEntity instance,
            String symbolCode,
            String side,
            String status,
            OffsetDateTime requestedAt) {
        TradeCycleLogEntity cycleLog = new TradeCycleLogEntity();
        cycleLog.setStrategyInstance(instance);
        cycleLog.setCycleStartedAt(requestedAt);
        cycleLog.setCycleFinishedAt(requestedAt.plusSeconds(2));
        cycleLog.setBusinessDate(requestedAt.toLocalDate());
        cycleLog.setCycleStage("COMPLETED");
        cycleLog = tradeCycleLogRepository.saveAndFlush(cycleLog);

        TradeDecisionLogEntity decision = new TradeDecisionLogEntity();
        decision.setTradeCycleLog(cycleLog);
        decision.setStrategyInstance(instance);
        decision.setCycleStartedAt(requestedAt);
        decision.setCycleFinishedAt(requestedAt.plusSeconds(2));
        decision.setBusinessDate(requestedAt.toLocalDate());
        decision.setCycleStatus("EXECUTE");
        decision.setSummary(symbolCode + " " + side);
        decision.setSettingsSnapshotJson(objectMapper.createObjectNode());
        decision = tradeDecisionLogRepository.saveAndFlush(decision);

        TradeOrderIntentEntity intent = new TradeOrderIntentEntity();
        intent.setTradeDecisionLog(decision);
        intent.setSequenceNo(1);
        intent.setSymbolCode(symbolCode);
        intent.setSide(side);
        intent.setQuantity(new BigDecimal("5.00000000"));
        intent.setOrderType("LIMIT");
        intent.setPrice(new BigDecimal("80000.00000000"));
        intent.setRationale("test");
        intent.setEvidenceJson(objectMapper.createArrayNode());
        intent = tradeOrderIntentRepository.saveAndFlush(intent);

        TradeOrderEntity order = new TradeOrderEntity();
        order.setTradeOrderIntent(intent);
        order.setStrategyInstance(instance);
        order.setClientOrderId("client-" + java.util.UUID.randomUUID());
        order.setExecutionMode("paper");
        order.setOrderStatus(status);
        order.setRequestedQuantity(new BigDecimal("5.00000000"));
        order.setRequestedPrice(new BigDecimal("80000.00000000"));
        order.setFilledQuantity(new BigDecimal("5.00000000"));
        order.setAvgFilledPrice(new BigDecimal("80000.00000000"));
        order.setRequestedAt(requestedAt);
        order.setAcceptedAt(requestedAt);
        order.setFilledAt(requestedAt);
        tradeOrderRepository.saveAndFlush(order);
    }

    private StrategyInstanceEntity createInstance(String name) {
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate(name + "-template", "prompt", modelProfile);

        StrategyInstanceEntity instance = new StrategyInstanceEntity();
        instance.setStrategyTemplate(template);
        instance.setName(name);
        instance.setLifecycleState("active");
        instance.setExecutionMode("paper");
        instance.setBudgetAmount(new BigDecimal("10000000.0000"));
        instance.setTradingModelProfile(modelProfile);
        instance = strategyInstanceRepository.saveAndFlush(instance);

        StrategyInstancePromptVersionEntity promptVersion = new StrategyInstancePromptVersionEntity();
        promptVersion.setStrategyInstance(instance);
        promptVersion.setVersionNo(1);
        promptVersion.setPromptText("prompt");
        promptVersion.setChangeNote("init");
        promptVersion = strategyInstancePromptVersionRepository.saveAndFlush(promptVersion);

        instance.setCurrentPromptVersion(promptVersion);
        return strategyInstanceRepository.saveAndFlush(instance);
    }

}
