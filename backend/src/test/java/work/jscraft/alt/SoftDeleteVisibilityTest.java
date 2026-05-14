package work.jscraft.alt;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateRepository;

import static org.assertj.core.api.Assertions.assertThat;

@PostgreSqlJpaTest
class SoftDeleteVisibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LlmModelProfileRepository llmModelProfileRepository;

    @Autowired
    private StrategyTemplateRepository strategyTemplateRepository;

    @Test
    void softDeletedEntitiesAreExcludedFromDefaultRepositoryQueries() {
        LlmModelProfileEntity modelProfile = new LlmModelProfileEntity();
        modelProfile.setPurpose("trading_decision");
        modelProfile.setProvider("openai");
        modelProfile.setModelName("gpt-5.5");
        modelProfile = llmModelProfileRepository.saveAndFlush(modelProfile);

        StrategyTemplateEntity template = new StrategyTemplateEntity();
        template.setName("soft-delete-template");
        template.setDescription("to be deleted");
        template.setDefaultCycleMinutes(10);
        template.setDefaultPromptText("prompt");
        template.setDefaultExecutionConfigJson(objectMapper.createObjectNode().put("slippageBps", 5));
        template.setDefaultTradingModelProfile(modelProfile);
        template = strategyTemplateRepository.saveAndFlush(template);

        strategyTemplateRepository.delete(template);
        strategyTemplateRepository.flush();
        entityManager.clear();

        assertThat(strategyTemplateRepository.findAll()).isEmpty();
        Integer rawCount = jdbcTemplate.queryForObject(
                "select count(*) from strategy_template where id = ?",
                Integer.class,
                template.getId());
        Integer liveCount = jdbcTemplate.queryForObject(
                "select count(*) from strategy_template where id = ? and deleted_at is null",
                Integer.class,
                template.getId());

        assertThat(rawCount).isEqualTo(1);
        assertThat(liveCount).isZero();
    }
}
