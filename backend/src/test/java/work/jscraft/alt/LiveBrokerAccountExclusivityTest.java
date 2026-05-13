package work.jscraft.alt;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.strategy.application.StrategyInstanceService;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveBrokerAccountExclusivityTest extends AdminCatalogApiIntegrationTestSupport {

    private static final String ACTIVATABLE_PROMPT = """
            ---
            sources:
              - {type: minute_bar}
            scope: held_only
            ---
            <system>Live exclusivity test prompt.</system>
            """;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void sameBrokerAccountCannotBeSharedByMultipleActiveLiveInstances() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("Live Template", ACTIVATABLE_PROMPT, modelProfile);
        BrokerAccountEntity brokerAccount = createBrokerAccount("KIS", "111122223333", "1111-****-3333");

        JsonNode firstInstance = createLiveInstance(adminLogin, template, brokerAccount, "KR 모멘텀 LIVE A");
        activate(adminLogin, firstInstance.path("id").asText(), firstInstance.path("version").asLong())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleState").value("active"));

        JsonNode secondInstance = createLiveInstance(adminLogin, template, brokerAccount, "KR 모멘텀 LIVE B");
        activate(adminLogin, secondInstance.path("id").asText(), secondInstance.path("version").asLong())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BROKER_ACCOUNT_ALREADY_IN_USE"));
    }

    private JsonNode createLiveInstance(
            LoginCookies adminLogin,
            StrategyTemplateEntity template,
            BrokerAccountEntity brokerAccount,
            String name) throws Exception {
        String body = mockMvc.perform(post("/api/admin/strategy-instances")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.CreateStrategyInstanceRequest(
                        template.getId(),
                        name,
                        "live",
                        brokerAccount.getId(),
                        new BigDecimal("1000000.0000"),
                        null,
                        null,
                        null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private org.springframework.test.web.servlet.ResultActions activate(
            LoginCookies adminLogin,
            String strategyInstanceId,
            long version) throws Exception {
        return mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/lifecycle", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.LifecycleChangeRequest("active", version))));
    }
}
