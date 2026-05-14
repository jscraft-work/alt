package work.jscraft.alt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import work.jscraft.alt.auth.infrastructure.persistence.AppUserEntity;
import work.jscraft.alt.auth.infrastructure.persistence.AppUserRepository;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.ops.infrastructure.persistence.SystemParameterEntity;
import work.jscraft.alt.ops.infrastructure.persistence.SystemParameterRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateRepository;

import static org.assertj.core.api.Assertions.assertThat;

@PostgreSqlJpaTest
class CoreRepositoryMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private LlmModelProfileRepository llmModelProfileRepository;

    @Autowired
    private AssetMasterRepository assetMasterRepository;

    @Autowired
    private SystemParameterRepository systemParameterRepository;

    @Autowired
    private BrokerAccountRepository brokerAccountRepository;

    @Autowired
    private StrategyTemplateRepository strategyTemplateRepository;

    @Test
    void coreEntitiesCanBePersistedAndLoaded() {
        LlmModelProfileEntity modelProfile = new LlmModelProfileEntity();
        modelProfile.setPurpose("trading_decision");
        modelProfile.setProvider("openai");
        modelProfile.setModelName("gpt-5.5");
        modelProfile.setEnabled(true);
        modelProfile = llmModelProfileRepository.saveAndFlush(modelProfile);

        StrategyTemplateEntity strategyTemplate = new StrategyTemplateEntity();
        strategyTemplate.setName("swing-template");
        strategyTemplate.setDescription("default swing strategy");
        strategyTemplate.setDefaultCycleMinutes(5);
        strategyTemplate.setDefaultPromptText("prompt");
        strategyTemplate.setDefaultExecutionConfigJson(json("slippageBps", 5));
        strategyTemplate.setDefaultTradingModelProfile(modelProfile);
        strategyTemplate = strategyTemplateRepository.saveAndFlush(strategyTemplate);

        BrokerAccountEntity brokerAccount = new BrokerAccountEntity();
        brokerAccount.setBrokerCode("KIS");
        brokerAccount.setBrokerAccountNo("12345678");
        brokerAccount.setAccountAlias("main");
        brokerAccount.setAccountMasked("1234-****");
        brokerAccount = brokerAccountRepository.saveAndFlush(brokerAccount);

        AssetMasterEntity assetMaster = new AssetMasterEntity();
        assetMaster.setSymbolCode("005930");
        assetMaster.setSymbolName("Samsung Electronics");
        assetMaster.setMarketType("KOSPI");
        assetMaster.setDartCorpCode("00126380");
        assetMaster.setHidden(false);
        assetMaster = assetMasterRepository.saveAndFlush(assetMaster);

        AppUserEntity appUser = new AppUserEntity();
        appUser.setLoginId("admin");
        appUser.setPasswordHash("{argon2}hash");
        appUser.setDisplayName("Administrator");
        appUser.setRoleCode("ADMIN");
        appUser.setEnabled(true);
        appUser = appUserRepository.saveAndFlush(appUser);

        SystemParameterEntity parameter = new SystemParameterEntity();
        parameter.setParameterKey("trading.window");
        parameter.setDescription("trading schedule");
        parameter.setValueJson(json("cycleMinutes", 5));
        systemParameterRepository.saveAndFlush(parameter);

        entityManager.clear();

        StrategyTemplateEntity loadedTemplate = strategyTemplateRepository.findById(strategyTemplate.getId()).orElseThrow();
        BrokerAccountEntity loadedBrokerAccount = brokerAccountRepository.findById(brokerAccount.getId()).orElseThrow();
        AssetMasterEntity loadedAsset = assetMasterRepository.findById(assetMaster.getId()).orElseThrow();
        AppUserEntity loadedUser = appUserRepository.findByLoginId("admin").orElseThrow();
        SystemParameterEntity loadedParameter = systemParameterRepository.findById("trading.window").orElseThrow();

        assertThat(loadedTemplate.getDefaultTradingModelProfile().getId()).isEqualTo(modelProfile.getId());
        assertThat(loadedBrokerAccount.getAccountMasked()).isEqualTo("1234-****");
        assertThat(loadedAsset.getSymbolName()).isEqualTo("Samsung Electronics");
        assertThat(loadedUser.getDisplayName()).isEqualTo("Administrator");
        assertThat(loadedParameter.getValueJson().path("cycleMinutes").asInt()).isEqualTo(5);
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
