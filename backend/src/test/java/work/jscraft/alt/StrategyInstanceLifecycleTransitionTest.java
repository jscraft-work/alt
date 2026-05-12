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

class StrategyInstanceLifecycleTransitionTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void draftActiveInactiveTransitionsAreAllowed() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("Lifecycle Template", "template prompt", modelProfile);
        JsonNode created = createDraftInstance(adminLogin, template);

        String strategyInstanceId = created.path("id").asText();
        long createVersion = created.path("version").asLong();

        String activeBody = mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/lifecycle", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.LifecycleChangeRequest("active", createVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleState").value("active"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long activeVersion = objectMapper.readTree(activeBody).path("data").path("version").asLong();

        String inactiveBody = mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/lifecycle", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.LifecycleChangeRequest("inactive", activeVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleState").value("inactive"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long inactiveVersion = objectMapper.readTree(inactiveBody).path("data").path("version").asLong();

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/lifecycle", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.LifecycleChangeRequest("draft", inactiveVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleState").value("draft"));
    }

    @Test
    void invalidLifecycleTargetStateIsRejected() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("Invalid Lifecycle", "template prompt", modelProfile);
        JsonNode created = createDraftInstance(adminLogin, template);

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/lifecycle", created.path("id").asText())
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "targetState": "archived",
                          "version": %d
                        }
                        """.formatted(created.path("version").asLong())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private JsonNode createDraftInstance(LoginCookies adminLogin, StrategyTemplateEntity template) throws Exception {
        String body = mockMvc.perform(post("/api/admin/strategy-instances")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.CreateStrategyInstanceRequest(
                        template.getId(),
                        "Lifecycle instance",
                        "paper",
                        null,
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
