package work.jscraft.alt;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.strategy.application.StrategyInstanceService;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.ops.infrastructure.persistence.AuditLogEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PromptVersionAdminApiTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void createPromptVersionAppendsAndMakesItCurrent() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("Prompt Template", "template prompt v1", modelProfile);
        JsonNode createdInstance = createStrategyInstance(adminLogin, template, "Prompt Instance", "paper", null, null);

        String strategyInstanceId = createdInstance.path("id").asText();
        UUID initialPromptVersionId = UUID.fromString(createdInstance.path("currentPromptVersionId").asText());

        mockMvc.perform(get("/api/admin/strategy-instances/{strategyInstanceId}/prompt-versions", strategyInstanceId)
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(initialPromptVersionId.toString()))
                .andExpect(jsonPath("$.data[0].versionNo").value(1))
                .andExpect(jsonPath("$.data[0].current").value(true));

        String createdBody = mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/prompt-versions", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.CreatePromptVersionRequest(
                        "template prompt v2",
                        "뉴스 입력 강화"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.promptText").value("template prompt v2"))
                .andExpect(jsonPath("$.data.current").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode newPromptVersion = objectMapper.readTree(createdBody).path("data");
        String newPromptVersionId = newPromptVersion.path("id").asText();

        mockMvc.perform(get("/api/admin/strategy-instances/{strategyInstanceId}/prompt-versions", strategyInstanceId)
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].current").value(false))
                .andExpect(jsonPath("$.data[1].id").value(newPromptVersionId))
                .andExpect(jsonPath("$.data[1].versionNo").value(2))
                .andExpect(jsonPath("$.data[1].current").value(true));

        assertThat(strategyInstanceRepository.findById(UUID.fromString(strategyInstanceId)).orElseThrow()
                .getCurrentPromptVersion()
                .getId())
                .hasToString(newPromptVersionId);
        assertThat(auditLogRepository.findAll().stream().map(AuditLogEntity::getActionType))
                .contains("STRATEGY_INSTANCE_PROMPT_VERSION_CREATED");
    }
}
