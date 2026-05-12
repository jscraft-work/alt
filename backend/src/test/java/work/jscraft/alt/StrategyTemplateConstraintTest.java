package work.jscraft.alt;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgreSqlJpaTest
class StrategyTemplateConstraintTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private LlmModelProfileRepository llmModelProfileRepository;

    @Autowired
    private StrategyTemplateRepository strategyTemplateRepository;

    @Test
    void defaultCycleMinutesMustRespectDatabaseCheckConstraint() {
        LlmModelProfileEntity modelProfile = new LlmModelProfileEntity();
        modelProfile.setPurpose("trading_decision");
        modelProfile.setProvider("openai");
        modelProfile.setModelName("gpt-5.5");
        modelProfile = llmModelProfileRepository.saveAndFlush(modelProfile);

        StrategyTemplateEntity invalidTemplate = new StrategyTemplateEntity();
        invalidTemplate.setName("invalid-template");
        invalidTemplate.setDescription("invalid");
        invalidTemplate.setDefaultCycleMinutes(0);
        invalidTemplate.setDefaultPromptText("prompt");
        invalidTemplate.setDefaultInputSpecJson(objectMapper.createObjectNode().put("scope", "held_only"));
        invalidTemplate.setDefaultExecutionConfigJson(objectMapper.createObjectNode().put("slippageBps", 5));
        invalidTemplate.setDefaultTradingModelProfile(modelProfile);

        assertThatThrownBy(() -> strategyTemplateRepository.saveAndFlush(invalidTemplate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
