package work.jscraft.alt;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.strategy.application.StrategyTemplateService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOptimisticLockApiTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void staleVersionReturnsConflictWithCurrentVersion() throws Exception {
        LoginCookies adminLogin = login();
        String modelProfileId = createTradingModelProfile().getId().toString();

        String createdBody = mockMvc.perform(post("/api/admin/strategy-templates")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyTemplateService.CreateStrategyTemplateRequest(
                        "KR 모멘텀 템플릿",
                        "기본 모멘텀 전략",
                        5,
                        "prompt-v1",
                        jsonObject("scope", "held_only"),
                        jsonObject("slippageBps", 5),
                        UUID.fromString(modelProfileId)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createdBody).path("data");
        String templateId = created.path("id").asText();

        mockMvc.perform(patch("/api/admin/strategy-templates/{strategyTemplateId}", templateId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyTemplateService.UpdateStrategyTemplateRequest(
                        "KR 모멘텀 템플릿 v2",
                        "수정 설명",
                        3,
                        "prompt-v2",
                        jsonObject("scope", "full_watchlist"),
                        jsonObject("slippageBps", 10),
                        UUID.fromString(modelProfileId),
                        0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(patch("/api/admin/strategy-templates/{strategyTemplateId}", templateId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyTemplateService.UpdateStrategyTemplateRequest(
                        "KR 모멘텀 템플릿 stale",
                        "stale update",
                        5,
                        "prompt-stale",
                        jsonObject("scope", "held_only"),
                        jsonObject("slippageBps", 5),
                        UUID.fromString(modelProfileId),
                        0L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OPTIMISTIC_LOCK_CONFLICT"))
                .andExpect(jsonPath("$.meta.currentVersion").value(1));
    }
}
