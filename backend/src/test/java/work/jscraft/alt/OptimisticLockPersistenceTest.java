package work.jscraft.alt;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgreSqlJpaTest
class OptimisticLockPersistenceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LlmModelProfileRepository llmModelProfileRepository;

    @Autowired
    private StrategyTemplateRepository strategyTemplateRepository;

    @Autowired
    private StrategyInstanceRepository strategyInstanceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void truncateCommittedTables() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE strategy_instance, strategy_template, llm_model_profile RESTART IDENTITY CASCADE");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void versionColumnRejectsStaleStrategyInstanceUpdate() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        StrategyInstanceEntity createdInstance = tx.execute(status -> strategyInstanceRepository.saveAndFlush(createStrategyInstance()));

        StrategyInstanceEntity firstCopy = tx.execute(
                status -> strategyInstanceRepository.findById(createdInstance.getId()).orElseThrow());
        StrategyInstanceEntity secondCopy = tx.execute(
                status -> strategyInstanceRepository.findById(createdInstance.getId()).orElseThrow());

        firstCopy.setName("updated-by-first-transaction");
        tx.execute(status -> strategyInstanceRepository.saveAndFlush(firstCopy));

        secondCopy.setName("stale-update");

        assertThatThrownBy(() -> tx.execute(status -> strategyInstanceRepository.saveAndFlush(secondCopy)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private StrategyInstanceEntity createStrategyInstance() {
        LlmModelProfileEntity modelProfile = new LlmModelProfileEntity();
        modelProfile.setPurpose("trading_decision");
        modelProfile.setProvider("openai");
        modelProfile.setModelName("gpt-5.5");
        modelProfile = llmModelProfileRepository.saveAndFlush(modelProfile);

        StrategyTemplateEntity template = new StrategyTemplateEntity();
        template.setName("optimistic-template");
        template.setDescription("optimistic template");
        template.setDefaultCycleMinutes(5);
        template.setDefaultPromptText("prompt");
        template.setDefaultExecutionConfigJson(json("slippageBps", 5));
        template.setDefaultTradingModelProfile(modelProfile);
        template = strategyTemplateRepository.saveAndFlush(template);

        StrategyInstanceEntity instance = new StrategyInstanceEntity();
        instance.setStrategyTemplate(template);
        instance.setName("optimistic-instance");
        instance.setLifecycleState("active");
        instance.setExecutionMode("paper");
        instance.setBudgetAmount(new BigDecimal("750000.0000"));
        instance.setTradingModelProfile(modelProfile);
        return instance;
    }

    private ObjectNode json(String fieldName, Object value) {
        ObjectNode node = objectMapper.createObjectNode();
        if (value instanceof Integer integer) {
            node.put(fieldName, integer);
        } else {
            node.put(fieldName, String.valueOf(value));
        }
        return node;
    }
}
