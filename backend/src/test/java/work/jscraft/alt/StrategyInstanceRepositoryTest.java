package work.jscraft.alt;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateRepository;

import static org.assertj.core.api.Assertions.assertThat;

@PostgreSqlJpaTest
class StrategyInstanceRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private LlmModelProfileRepository llmModelProfileRepository;

    @Autowired
    private StrategyTemplateRepository strategyTemplateRepository;

    @Autowired
    private StrategyInstanceRepository strategyInstanceRepository;

    @Autowired
    private StrategyInstancePromptVersionRepository promptVersionRepository;

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @Autowired
    private AssetMasterRepository assetMasterRepository;

    @Test
    void strategyInstancePersistsPromptVersionsAndWatchlistRelations() {
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate(modelProfile);
        StrategyInstanceEntity instance = new StrategyInstanceEntity();
        instance.setStrategyTemplate(template);
        instance.setName("swing-paper-1");
        instance.setLifecycleState("active");
        instance.setExecutionMode("paper");
        instance.setBudgetAmount(new BigDecimal("1000000.0000"));
        instance.setTradingModelProfile(modelProfile);
        instance.setExecutionConfigOverrideJson(json("slippageBps", 5));
        instance = strategyInstanceRepository.saveAndFlush(instance);

        StrategyInstancePromptVersionEntity promptVersion = new StrategyInstancePromptVersionEntity();
        promptVersion.setStrategyInstance(instance);
        promptVersion.setVersionNo(1);
        promptVersion.setPromptText("analyze current watchlist and holdings");
        promptVersion.setChangeNote("initial import");
        promptVersion = promptVersionRepository.saveAndFlush(promptVersion);

        instance.setCurrentPromptVersion(promptVersion);
        strategyInstanceRepository.saveAndFlush(instance);

        AssetMasterEntity samsung = createAsset("101010", "Samsung Electronics");
        AssetMasterEntity skHynix = createAsset("202020", "SK hynix");

        StrategyInstanceWatchlistRelationEntity samsungRelation = new StrategyInstanceWatchlistRelationEntity();
        samsungRelation.setStrategyInstance(instance);
        samsungRelation.setAssetMaster(samsung);
        watchlistRelationRepository.saveAndFlush(samsungRelation);

        StrategyInstanceWatchlistRelationEntity hynixRelation = new StrategyInstanceWatchlistRelationEntity();
        hynixRelation.setStrategyInstance(instance);
        hynixRelation.setAssetMaster(skHynix);
        watchlistRelationRepository.saveAndFlush(hynixRelation);

        entityManager.clear();

        StrategyInstanceEntity loadedInstance = strategyInstanceRepository.findById(instance.getId()).orElseThrow();
        List<StrategyInstancePromptVersionEntity> promptVersions =
                promptVersionRepository.findByStrategyInstanceIdOrderByVersionNoAsc(instance.getId());
        List<StrategyInstanceWatchlistRelationEntity> watchlist =
                watchlistRelationRepository.findByStrategyInstanceId(instance.getId());

        assertThat(loadedInstance.getCurrentPromptVersion().getId()).isEqualTo(promptVersion.getId());
        assertThat(promptVersions).hasSize(1);
        assertThat(promptVersions.getFirst().getPromptText()).isEqualTo("analyze current watchlist and holdings");
        assertThat(watchlist).hasSize(2);
        assertThat(watchlist)
                .extracting(relation -> relation.getAssetMaster().getSymbolCode())
                .containsExactlyInAnyOrder("101010", "202020");
    }

    private LlmModelProfileEntity createTradingModelProfile() {
        LlmModelProfileEntity modelProfile = new LlmModelProfileEntity();
        modelProfile.setPurpose("trading_decision");
        modelProfile.setProvider("openai");
        modelProfile.setModelName("gpt-5.5");
        modelProfile.setEnabled(true);
        return llmModelProfileRepository.saveAndFlush(modelProfile);
    }

    private StrategyTemplateEntity createStrategyTemplate(LlmModelProfileEntity modelProfile) {
        StrategyTemplateEntity template = new StrategyTemplateEntity();
        template.setName("swing-template");
        template.setDescription("default swing strategy");
        template.setDefaultCycleMinutes(5);
        template.setDefaultPromptText("default prompt");
        template.setDefaultExecutionConfigJson(json("slippageBps", 3));
        template.setDefaultTradingModelProfile(modelProfile);
        return strategyTemplateRepository.saveAndFlush(template);
    }

    private AssetMasterEntity createAsset(String symbolCode, String symbolName) {
        AssetMasterEntity assetMaster = new AssetMasterEntity();
        assetMaster.setSymbolCode(symbolCode);
        assetMaster.setSymbolName(symbolName);
        assetMaster.setMarketType("KOSPI");
        assetMaster.setHidden(false);
        return assetMasterRepository.saveAndFlush(assetMaster);
    }

    private ObjectNode json(String fieldName, Object value) {
        ObjectNode node = objectMapper.createObjectNode();
        if (value instanceof Integer integer) {
            node.put(fieldName, integer);
        } else if (value instanceof Double doubleValue) {
            node.put(fieldName, doubleValue);
        } else if (value instanceof Boolean booleanValue) {
            node.put(fieldName, booleanValue);
        } else {
            node.put(fieldName, String.valueOf(value));
        }
        return node;
    }
}
