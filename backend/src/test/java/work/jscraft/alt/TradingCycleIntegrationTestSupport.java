package work.jscraft.alt;

import java.math.BigDecimal;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;

abstract class TradingCycleIntegrationTestSupport extends CollectorIntegrationTestSupport {

    /**
     * 최소 유효 prompt: 사이클이 {@code PromptInputSpecParser → PromptContextAssembler → PromptTemplateEngine}을
     * 통과하는데 필요한 frontmatter만 둔다. LLM 응답은 fake가 결정하므로 본문은 자리표시자로 충분.
     */
    protected static final String DEFAULT_CYCLE_PROMPT = """
            ---
            minute_bars: 60
            fundamental: true
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
