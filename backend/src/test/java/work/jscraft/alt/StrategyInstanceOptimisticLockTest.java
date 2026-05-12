package work.jscraft.alt;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.strategy.application.StrategyInstanceService;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StrategyInstanceOptimisticLockTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void staleVersionReturnsConflictWithCurrentVersion() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("Optimistic Template", "template prompt", modelProfile);

        String createdBody = mockMvc.perform(post("/api/admin/strategy-instances")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.CreateStrategyInstanceRequest(
                        template.getId(),
                        "Optimistic Instance",
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

        JsonNode created = objectMapper.readTree(createdBody).path("data");
        String strategyInstanceId = created.path("id").asText();
        long createdVersion = created.path("version").asLong();

        String updatedBody = mockMvc.perform(patch("/api/admin/strategy-instances/{strategyInstanceId}", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.UpdateStrategyInstanceRequest(
                        "Optimistic Instance v2",
                        "paper",
                        null,
                        new BigDecimal("1200000.0000"),
                        modelProfile.getId(),
                        jsonObject("scope", "held_only"),
                        jsonObject("slippageBps", 7),
                        createdVersion))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long updatedVersion = objectMapper.readTree(updatedBody).path("data").path("version").asLong();

        mockMvc.perform(patch("/api/admin/strategy-instances/{strategyInstanceId}", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.UpdateStrategyInstanceRequest(
                        "Optimistic Instance stale",
                        "paper",
                        null,
                        new BigDecimal("1300000.0000"),
                        modelProfile.getId(),
                        jsonObject("scope", "held_only"),
                        jsonObject("slippageBps", 9),
                        createdVersion))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OPTIMISTIC_LOCK_CONFLICT"))
                .andExpect(jsonPath("$.meta.currentVersion").value(updatedVersion));
    }
}
