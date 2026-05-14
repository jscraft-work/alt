package work.jscraft.alt;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.strategy.application.StrategyInstanceService;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PromptRestoreTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void restoringOldPromptCreatesNewVersionInsteadOfPointingBack() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("Restore Template", "template prompt v1", modelProfile);
        JsonNode createdInstance = createStrategyInstance(adminLogin, template, "Restore Instance", "paper", null);

        String strategyInstanceId = createdInstance.path("id").asText();
        String versionOneId = createdInstance.path("currentPromptVersionId").asText();

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/prompt-versions", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.CreatePromptVersionRequest(
                        "template prompt v2",
                        "second version"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2));

        StrategyInstanceEntity strategyInstance = strategyInstanceRepository.findById(UUID.fromString(strategyInstanceId)).orElseThrow();
        long currentVersion = strategyInstance.getVersion();

        String restoredBody = mockMvc.perform(post(
                "/api/admin/strategy-instances/{strategyInstanceId}/prompt-versions/{promptVersionId}/activate",
                strategyInstanceId,
                versionOneId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.ActivatePromptVersionRequest(currentVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(3))
                .andExpect(jsonPath("$.data.promptText").value("template prompt v1"))
                .andExpect(jsonPath("$.data.current").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String restoredPromptVersionId = objectMapper.readTree(restoredBody).path("data").path("id").asText();
        assertThat(restoredPromptVersionId).isNotEqualTo(versionOneId);

        mockMvc.perform(get("/api/admin/strategy-instances/{strategyInstanceId}/prompt-versions", strategyInstanceId)
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].versionNo").value(1))
                .andExpect(jsonPath("$.data[0].current").value(false))
                .andExpect(jsonPath("$.data[1].versionNo").value(2))
                .andExpect(jsonPath("$.data[1].current").value(false))
                .andExpect(jsonPath("$.data[2].id").value(restoredPromptVersionId))
                .andExpect(jsonPath("$.data[2].versionNo").value(3))
                .andExpect(jsonPath("$.data[2].changeNote").value("Restored from version 1"))
                .andExpect(jsonPath("$.data[2].current").value(true));
    }
}
