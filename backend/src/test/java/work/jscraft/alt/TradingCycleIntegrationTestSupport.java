package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.marketdata.OrderBookRedisCache;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.OrderBookSnapshot;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;

abstract class TradingCycleIntegrationTestSupport extends CollectorIntegrationTestSupport {

    @Autowired
    protected OrderBookRedisCache orderBookRedisCache;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * KIS WS push schema 형식의 호가 5 단계를 Redis 에 seed.
     *
     * <p>{@link PaperOrderExecutor} 의 walker 가 {@link OrderBookRedisCache#read} 로 읽어 호가 walk
     * + 한 틱 양보 시뮬에 사용한다. paper E2E 가 walker 동작 의존하므로 setup 에 반드시 호출.
     *
     * <p>{@code askPrices[0]} = 매도 1 단계 (= ask1, 가장 낮은 매도호가, BUY 의 즉시 체결가).
     * {@code bidPrices[0]} = 매수 1 단계 (= bid1, 가장 높은 매수호가, SELL 의 즉시 체결가).
     */
    protected void seedOrderbook(
            String symbolCode,
            List<Long> askPrices,
            List<Long> askVolumes,
            List<Long> bidPrices,
            List<Long> bidVolumes) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode ap = payload.putArray("ask_prices");
        askPrices.forEach(ap::add);
        ArrayNode bp = payload.putArray("bid_prices");
        bidPrices.forEach(bp::add);
        ArrayNode av = payload.putArray("ask_volumes");
        askVolumes.forEach(av::add);
        ArrayNode bv = payload.putArray("bid_volumes");
        bidVolumes.forEach(bv::add);

        OrderBookSnapshot snapshot = new OrderBookSnapshot(
                symbolCode,
                OffsetDateTime.now(mutableClock),
                payload,
                "kis-ws");
        orderBookRedisCache.store(snapshot);
    }


    /**
     * 최소 유효 prompt: 사이클이 {@code PromptInputSpecParser → PromptContextAssembler → PromptTemplateEngine}을
     * 통과하는데 필요한 frontmatter만 둔다. LLM 응답은 fake가 결정하므로 본문은 자리표시자로 충분.
     */
    protected static final String DEFAULT_CYCLE_PROMPT = """
            ---
            scope: full_watchlist
            ---
            <system>Test prompt.</system>
            {% for s in stocks %}
            <stock code="{{ s.code }}">{{ s.minute_bars }}</stock>
            {% endfor %}
            응답: {"cycleStatus":"HOLD","summary":"test","orders":[]}
            """;

    protected StrategyInstanceEntity createActiveInstance(String name, String executionMode) {
        return createInstanceWithState(name, executionMode, "active", null);
    }

    protected StrategyInstanceEntity createInstanceWithState(
            String name,
            String executionMode,
            String lifecycleState,
            String autoPausedReason) {
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate(name + "-template", DEFAULT_CYCLE_PROMPT, modelProfile);

        StrategyInstanceEntity instance = new StrategyInstanceEntity();
        instance.setStrategyTemplate(template);
        instance.setName(name);
        instance.setLifecycleState(lifecycleState);
        instance.setExecutionMode(executionMode);
        instance.setBudgetAmount(new BigDecimal("10000000.0000"));
        instance.setTradingModelProfile(modelProfile);
        instance.setAutoPausedReason(autoPausedReason);
        if (autoPausedReason != null) {
            instance.setAutoPausedAt(java.time.OffsetDateTime.now(mutableClock));
        }
        instance = strategyInstanceRepository.saveAndFlush(instance);

        StrategyInstancePromptVersionEntity promptVersion = new StrategyInstancePromptVersionEntity();
        promptVersion.setStrategyInstance(instance);
        promptVersion.setVersionNo(1);
        promptVersion.setPromptText(DEFAULT_CYCLE_PROMPT);
        promptVersion.setChangeNote("init");
        promptVersion = strategyInstancePromptVersionRepository.saveAndFlush(promptVersion);

        instance.setCurrentPromptVersion(promptVersion);
        return strategyInstanceRepository.saveAndFlush(instance);
    }

    protected StrategyInstanceEntity createActiveLiveInstance(String name) {
        BrokerAccountEntity brokerAccount = createBrokerAccount("KIS", "12345-67",
                "12345-***");
        StrategyInstanceEntity instance = createActiveInstance(name, "live");
        instance.setBrokerAccount(brokerAccount);
        return strategyInstanceRepository.saveAndFlush(instance);
    }

    protected StrategyInstancePromptVersionEntity addPromptVersion(
            StrategyInstanceEntity instance,
            int versionNo,
            String text,
            String note) {
        StrategyInstancePromptVersionEntity promptVersion = new StrategyInstancePromptVersionEntity();
        promptVersion.setStrategyInstance(instance);
        promptVersion.setVersionNo(versionNo);
        promptVersion.setPromptText(text);
        promptVersion.setChangeNote(note);
        return strategyInstancePromptVersionRepository.saveAndFlush(promptVersion);
    }
}
