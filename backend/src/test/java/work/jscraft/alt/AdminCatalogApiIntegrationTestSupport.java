package work.jscraft.alt;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import work.jscraft.alt.strategy.application.StrategyInstanceService;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateRepository;

abstract class AdminCatalogApiIntegrationTestSupport extends AuthApiIntegrationTestSupport {

    @Autowired
    protected LlmModelProfileRepository llmModelProfileRepository;

    @Autowired
    protected StrategyTemplateRepository strategyTemplateRepository;

    @Autowired
    protected StrategyInstanceRepository strategyInstanceRepository;

    @Autowired
    protected StrategyInstancePromptVersionRepository strategyInstancePromptVersionRepository;

    @Autowired
    protected BrokerAccountRepository brokerAccountRepository;

    @Autowired
    protected AssetMasterRepository assetMasterRepository;

    protected void resetAdminCatalogState() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    macro_item,
                    market_minute_item,
                    market_price_item,
                    news_asset_relation,
                    news_item,
                    disclosure_item,
                    trade_order,
                    trade_order_intent,
                    trade_decision_log,
                    trade_cycle_log,
                    portfolio_position,
                    portfolio,
                    ops_event,
                    strategy_instance_watchlist_relation,
                    strategy_instance_prompt_version,
                    strategy_instance,
                    strategy_template,
                    llm_model_profile,
                    system_parameter,
                    asset_master,
                    broker_account,
                    audit_log,
                    app_user
                CASCADE
                """);
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        mutableClock.setInstant(AuthTestClockConfiguration.DEFAULT_INSTANT);
    }

    protected LlmModelProfileEntity createTradingModelProfile() {
        LlmModelProfileEntity modelProfile = new LlmModelProfileEntity();
        modelProfile.setPurpose("trading_decision");
        modelProfile.setProvider("openclaw");
        modelProfile.setModelName("gpt-5.5");
        modelProfile.setEnabled(true);
        return llmModelProfileRepository.saveAndFlush(modelProfile);
    }

    protected StrategyTemplateEntity createStrategyTemplate(
            String name,
            String promptText,
            LlmModelProfileEntity modelProfile) {
        StrategyTemplateEntity template = new StrategyTemplateEntity();
        template.setName(name);
        template.setDescription(name + " description");
        template.setDefaultCycleMinutes(5);
        template.setDefaultPromptText(promptText);
        template.setDefaultInputSpecJson(jsonObject("scope", "held_only"));
        template.setDefaultExecutionConfigJson(jsonObject("slippageBps", 5));
        template.setDefaultTradingModelProfile(modelProfile);
        return strategyTemplateRepository.saveAndFlush(template);
    }

    protected BrokerAccountEntity createBrokerAccount(String brokerCode, String accountNo, String masked) {
        BrokerAccountEntity brokerAccount = new BrokerAccountEntity();
        brokerAccount.setBrokerCode(brokerCode);
        brokerAccount.setBrokerAccountNo(accountNo);
        brokerAccount.setAccountAlias(brokerCode + " main");
        brokerAccount.setAccountMasked(masked);
        return brokerAccountRepository.saveAndFlush(brokerAccount);
    }

    protected AssetMasterEntity createAsset(String symbolCode, String symbolName, String dartCorpCode) {
        AssetMasterEntity assetMaster = new AssetMasterEntity();
        assetMaster.setSymbolCode(symbolCode);
        assetMaster.setSymbolName(symbolName);
        assetMaster.setMarketType("KOSPI");
        assetMaster.setDartCorpCode(dartCorpCode);
        assetMaster.setHidden(false);
        return assetMasterRepository.saveAndFlush(assetMaster);
    }

    protected JsonNode createStrategyInstance(
            LoginCookies adminLogin,
            StrategyTemplateEntity template,
            String name,
            String executionMode,
            java.util.UUID brokerAccountId,
            JsonNode inputSpecOverride) throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/strategy-instances")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.CreateStrategyInstanceRequest(
                        template.getId(),
                        name,
                        executionMode,
                        brokerAccountId,
                        new BigDecimal("1000000.0000"),
                        null,
                        inputSpecOverride,
                        null))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    protected ObjectNode jsonObject(String fieldName, Object value) {
        ObjectNode node = objectMapper.createObjectNode();
        if (value instanceof Integer integer) {
            node.put(fieldName, integer);
        } else if (value instanceof Long longValue) {
            node.put(fieldName, longValue);
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
