package work.jscraft.alt;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateRepository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgreSqlJpaTest
class WatchlistUniqueConstraintTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private LlmModelProfileRepository llmModelProfileRepository;

    @Autowired
    private StrategyTemplateRepository strategyTemplateRepository;

    @Autowired
    private StrategyInstanceRepository strategyInstanceRepository;

    @Autowired
    private AssetMasterRepository assetMasterRepository;

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void truncateCommittedTables() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE strategy_instance_watchlist_relation, strategy_instance,"
                        + " strategy_template, asset_master, llm_model_profile RESTART IDENTITY CASCADE");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void liveWatchlistRelationMustBeUniqueUntilSoftDeleted() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        StrategyInstanceEntity instance = tx.execute(status -> createStrategyInstance());
        AssetMasterEntity assetMaster = tx.execute(status -> createAsset());
        StrategyInstanceWatchlistRelationEntity firstRelation = tx.execute(status -> {
            StrategyInstanceWatchlistRelationEntity relation = new StrategyInstanceWatchlistRelationEntity();
            relation.setStrategyInstance(instance);
            relation.setAssetMaster(assetMaster);
            return watchlistRelationRepository.saveAndFlush(relation);
        });

        assertThatThrownBy(() -> tx.execute(status -> {
            StrategyInstanceWatchlistRelationEntity duplicateRelation = new StrategyInstanceWatchlistRelationEntity();
            duplicateRelation.setStrategyInstance(instance);
            duplicateRelation.setAssetMaster(assetMaster);
            return watchlistRelationRepository.saveAndFlush(duplicateRelation);
        }))
                .isInstanceOf(DataIntegrityViolationException.class);

        tx.executeWithoutResult(status -> {
            StrategyInstanceWatchlistRelationEntity relationToDelete =
                    watchlistRelationRepository.findById(firstRelation.getId()).orElseThrow();
            watchlistRelationRepository.delete(relationToDelete);
            watchlistRelationRepository.flush();
        });

        assertThatCode(() -> tx.execute(status -> {
            StrategyInstanceWatchlistRelationEntity recreatedRelation = new StrategyInstanceWatchlistRelationEntity();
            recreatedRelation.setStrategyInstance(instance);
            recreatedRelation.setAssetMaster(assetMaster);
            return watchlistRelationRepository.saveAndFlush(recreatedRelation);
        }))
                .doesNotThrowAnyException();
    }

    private StrategyInstanceEntity createStrategyInstance() {
        LlmModelProfileEntity modelProfile = new LlmModelProfileEntity();
        modelProfile.setPurpose("trading_decision");
        modelProfile.setProvider("openai");
        modelProfile.setModelName("gpt-5.5");
        modelProfile = llmModelProfileRepository.saveAndFlush(modelProfile);

        StrategyTemplateEntity template = new StrategyTemplateEntity();
        template.setName("watchlist-template");
        template.setDescription("watchlist template");
        template.setDefaultCycleMinutes(5);
        template.setDefaultPromptText("prompt");
        template.setDefaultExecutionConfigJson(json("slippageBps", 5));
        template.setDefaultTradingModelProfile(modelProfile);
        template = strategyTemplateRepository.saveAndFlush(template);

        StrategyInstanceEntity instance = new StrategyInstanceEntity();
        instance.setStrategyTemplate(template);
        instance.setName("watchlist-instance");
        instance.setLifecycleState("active");
        instance.setExecutionMode("paper");
        instance.setBudgetAmount(new BigDecimal("500000.0000"));
        instance.setTradingModelProfile(modelProfile);
        return strategyInstanceRepository.saveAndFlush(instance);
    }

    private AssetMasterEntity createAsset() {
        AssetMasterEntity assetMaster = new AssetMasterEntity();
        assetMaster.setSymbolCode("005930");
        assetMaster.setSymbolName("Samsung Electronics");
        assetMaster.setMarketType("KOSPI");
        assetMaster.setHidden(false);
        return assetMasterRepository.saveAndFlush(assetMaster);
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
