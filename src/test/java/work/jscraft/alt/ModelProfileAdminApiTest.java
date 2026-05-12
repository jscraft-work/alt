package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.llm.application.LlmModelProfileService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModelProfileAdminApiTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void modelProfilesSupportCrudAndValidation() throws Exception {
        LoginCookies adminLogin = login();

        LlmModelProfileService.CreateModelProfileRequest createRequest =
                new LlmModelProfileService.CreateModelProfileRequest(
                        "daily_report",
                        "openai",
                        "gpt-5.5-mini",
                        true);

        String createdBody = mockMvc.perform(post("/api/admin/model-profiles")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purpose").value("daily_report"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String modelProfileId = objectMapper.readTree(createdBody).path("data").path("id").asText();

        mockMvc.perform(get("/api/admin/model-profiles")
                .cookie(adminLogin.sessionCookie())
                .param("purpose", "daily_report")
                .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(modelProfileId));

        LlmModelProfileService.CreateModelProfileRequest invalidRequest =
                new LlmModelProfileService.CreateModelProfileRequest(
                        "invalid",
                        "openai",
                        "gpt-5.5",
                        true);

        mockMvc.perform(post("/api/admin/model-profiles")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("purpose"));

        LlmModelProfileService.UpdateModelProfileRequest updateRequest =
                new LlmModelProfileService.UpdateModelProfileRequest(
                        "daily_report",
                        "openai",
                        "gpt-5.5-mini",
                        false,
                        0L);

        mockMvc.perform(patch("/api/admin/model-profiles/{modelProfileId}", modelProfileId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get("/api/admin/model-profiles")
                .cookie(adminLogin.sessionCookie())
                .param("purpose", "daily_report")
                .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(modelProfileId));

        mockMvc.perform(delete("/api/admin/model-profiles/{modelProfileId}", modelProfileId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }
}
