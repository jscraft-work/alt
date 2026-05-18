package work.jscraft.alt;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.strategy.application.StrategyInstanceService;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StrategyInstanceActivationTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void activationFailsWhenCurrentPromptIsMissing() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("Prompt Missing", "template prompt", modelProfile);

        JsonNode created = createInstance(adminLogin, template, "paper", null);
        String strategyInstanceId = created.path("id").asText();
        long version = created.path("version").asLong();

        jdbcTemplate.update("update strategy_instance set current_prompt_version_id = null where id = ?",
                java.util.UUID.fromString(strategyInstanceId));

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/lifecycle", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.LifecycleChangeRequest("active", version))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSTANCE_NOT_ACTIVATABLE"));
    }

    @Test
    void liveActivationRequiresBrokerAccount() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("Live No Broker", "template prompt", modelProfile);

        JsonNode created = createInstance(adminLogin, template, "live", null);
        String strategyInstanceId = created.path("id").asText();
        long version = created.path("version").asLong();

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/lifecycle", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.LifecycleChangeRequest("active", version))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSTANCE_NOT_ACTIVATABLE"));
    }

    private JsonNode createInstance(LoginCookies adminLogin, StrategyTemplateEntity template, String executionMode, java.util.UUID brokerAccountId)
            throws Exception {
        String body = mockMvc.perform(post("/api/admin/strategy-instances")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.CreateStrategyInstanceRequest(
                        template.getId(),
                        template.getName() + "-instance",
                        executionMode,
                        brokerAccountId,
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
}
