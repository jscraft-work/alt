package work.jscraft.alt;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.strategy.application.StrategyInstanceService;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.ops.infrastructure.persistence.AuditLogEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StrategyInstanceAdminApiTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void createUpdateReadAndDeactivateStrategyInstance() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("KR 모멘텀 템플릿", "template prompt v1", modelProfile);
        BrokerAccountEntity brokerAccount = createBrokerAccount("KIS", "1234567890", "1234-****-7890");

        String createdBody = mockMvc.perform(post("/api/admin/strategy-instances")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.CreateStrategyInstanceRequest(
                        template.getId(),
                        "KR 모멘텀 A",
                        "paper",
                        null,
                        new BigDecimal("10000000.0000"),
                        null,
                        null,
                        null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategyTemplateId").value(template.getId().toString()))
                .andExpect(jsonPath("$.data.lifecycleState").value("draft"))
                .andExpect(jsonPath("$.data.executionMode").value("paper"))
                .andExpect(jsonPath("$.data.tradingModelProfileId").value(modelProfile.getId().toString()))
                .andExpect(jsonPath("$.data.inputSpecOverride.scope").value("held_only"))
                .andExpect(jsonPath("$.data.executionConfigOverride.slippageBps").value(5))
                .andExpect(jsonPath("$.data.currentPromptVersionId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createdBody).path("data");
        String strategyInstanceId = created.path("id").asText();
        long createdVersion = created.path("version").asLong();

        List<StrategyInstancePromptVersionEntity> promptVersions =
                strategyInstancePromptVersionRepository.findByStrategyInstanceIdOrderByVersionNoAsc(UUID.fromString(strategyInstanceId));
        assertThat(promptVersions).hasSize(1);
        assertThat(promptVersions.getFirst().getPromptText()).isEqualTo("template prompt v1");

        String updatedBody = mockMvc.perform(patch("/api/admin/strategy-instances/{strategyInstanceId}", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.UpdateStrategyInstanceRequest(
                        "KR 모멘텀 LIVE",
                        "live",
                        brokerAccount.getId(),
                        new BigDecimal("12000000.0000"),
                        modelProfile.getId(),
                        jsonObject("scope", "full_watchlist"),
                        jsonObject("slippageBps", 10),
                        createdVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("KR 모멘텀 LIVE"))
                .andExpect(jsonPath("$.data.executionMode").value("live"))
                .andExpect(jsonPath("$.data.brokerAccountId").value(brokerAccount.getId().toString()))
                .andExpect(jsonPath("$.data.brokerAccountMasked").value("1234-****-7890"))
                .andExpect(jsonPath("$.data.inputSpecOverride.scope").value("full_watchlist"))
                .andExpect(jsonPath("$.data.executionConfigOverride.slippageBps").value(10))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode updated = objectMapper.readTree(updatedBody).path("data");
        long updatedVersion = updated.path("version").asLong();

        mockMvc.perform(get("/api/admin/strategy-instances")
                .queryParam("lifecycleState", "draft")
                .queryParam("executionMode", "live")
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(strategyInstanceId))
                .andExpect(jsonPath("$.data[0].name").value("KR 모멘텀 LIVE"));

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/lifecycle", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.LifecycleChangeRequest("inactive", updatedVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleState").value("inactive"));

        List<String> actionTypes = auditLogRepository.findAll().stream()
                .map(AuditLogEntity::getActionType)
                .toList();
        assertThat(actionTypes).contains(
                "STRATEGY_INSTANCE_CREATED",
                "STRATEGY_INSTANCE_UPDATED",
                "STRATEGY_INSTANCE_LIFECYCLE_CHANGED");
    }
}
