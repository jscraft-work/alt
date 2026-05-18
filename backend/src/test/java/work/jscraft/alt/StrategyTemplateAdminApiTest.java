package work.jscraft.alt;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.strategy.application.StrategyTemplateService;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StrategyTemplateAdminApiTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
        createUser("viewer", "Password!123", "조회자", "VIEWER");
    }

    @Test
    void templateReadEndpointIsPublicButWritesRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/strategy-templates"))
                .andExpect(status().isOk());

        LoginCookies viewerLogin = login("viewer", "Password!123", "198.51.100.55");

        mockMvc.perform(get("/api/admin/strategy-templates")
                .cookie(viewerLogin.sessionCookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/strategy-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "KR 모멘텀 템플릿",
                          "description": "기본 모멘텀 전략",
                          "defaultCycleMinutes": 5,
                          "defaultPromptText": "prompt-v1",
                          "defaultExecutionConfig": { "slippageBps": 5 },
                          "defaultTradingModelProfileId": "%s"
                        }
                        """.formatted(createTradingModelProfile().getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/strategy-templates")
                .cookie(viewerLogin.sessionCookie(), viewerLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), viewerLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "KR 모멘텀 템플릿",
                          "description": "기본 모멘텀 전략",
                          "defaultCycleMinutes": 5,
                          "defaultPromptText": "prompt-v1",
                          "defaultExecutionConfig": { "slippageBps": 5 },
                          "defaultTradingModelProfileId": "%s"
                        }
                        """.formatted(createTradingModelProfile().getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUpdateReadDeleteStrategyTemplateAndRecordAudit() throws Exception {
        LoginCookies adminLogin = login();
        String modelProfileId = createTradingModelProfile().getId().toString();

        StrategyTemplateService.CreateStrategyTemplateRequest createRequest =
                new StrategyTemplateService.CreateStrategyTemplateRequest(
                        "KR 모멘텀 템플릿",
                        "기본 모멘텀 전략",
                        5,
                        "prompt-v1",
                        jsonObject("slippageBps", 5),
                        UUID.fromString(modelProfileId));

        String createdBody = mockMvc.perform(post("/api/admin/strategy-templates")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("KR 모멘텀 템플릿"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createdBody).path("data");
        String templateId = created.path("id").asText();

        mockMvc.perform(get("/api/admin/strategy-templates")
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(templateId))
                .andExpect(jsonPath("$.data[0].defaultTradingModelProfileId").value(modelProfileId));

        StrategyTemplateService.UpdateStrategyTemplateRequest updateRequest =
                new StrategyTemplateService.UpdateStrategyTemplateRequest(
                        "KR 모멘텀 템플릿 v2",
                        "수정 설명",
                        3,
                        "prompt-v2",
                        jsonObject("slippageBps", 10),
                        UUID.fromString(modelProfileId),
                        0L);

        mockMvc.perform(patch("/api/admin/strategy-templates/{strategyTemplateId}", templateId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("KR 모멘텀 템플릿 v2"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(delete("/api/admin/strategy-templates/{strategyTemplateId}", templateId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(get("/api/admin/strategy-templates")
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        Integer deletedCount = jdbcTemplate.queryForObject(
                "select count(*) from strategy_template where id = ? and deleted_at is not null",
                Integer.class,
                UUID.fromString(templateId));

        assertThat(deletedCount).isEqualTo(1);
        assertThat(auditLogRepository.findAll()).extracting("actionType")
                .contains(
                        "STRATEGY_TEMPLATE_CREATED",
                        "STRATEGY_TEMPLATE_UPDATED",
                        "STRATEGY_TEMPLATE_DELETED");
    }

    @Test
    void templateCycleChangeMarksInheritedInstancesScheduleDirty() throws Exception {
        LoginCookies adminLogin = login();
        var modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("KR 모멘텀 템플릿", "prompt-v1", modelProfile);
        StrategyInstanceEntity inherited = strategyInstanceRepository.findById(UUID.fromString(
                createStrategyInstance(adminLogin, template, "Inherited", "paper", null).path("id").asText())).orElseThrow();
        StrategyInstanceEntity overridden = strategyInstanceRepository.findById(UUID.fromString(
                createStrategyInstance(adminLogin, template, "Overridden", "paper", null).path("id").asText())).orElseThrow();
        overridden.setCycleMinutes(3);
        overridden.setScheduleDirty(false);
        strategyInstanceRepository.saveAndFlush(overridden);

        mockMvc.perform(patch("/api/admin/strategy-templates/{strategyTemplateId}", template.getId())
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyTemplateService.UpdateStrategyTemplateRequest(
                        template.getName(),
                        template.getDescription(),
                        7,
                        template.getDefaultPromptText(),
                        template.getDefaultExecutionConfigJson(),
                        template.getDefaultTradingModelProfile().getId(),
                        template.getVersion()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultCycleMinutes").value(7));

        assertThat(strategyInstanceRepository.findById(inherited.getId()).orElseThrow().isScheduleDirty()).isTrue();
        assertThat(strategyInstanceRepository.findById(overridden.getId()).orElseThrow().isScheduleDirty()).isFalse();
    }
}
