package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.ops.application.SystemParameterService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SystemParameterAdminApiTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void systemParametersSupportCrudAndVersionConflict() throws Exception {
        LoginCookies adminLogin = login();

        mockMvc.perform(post("/api/admin/system-parameters")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new SystemParameterService.CreateSystemParameterRequest(
                        "trading.window",
                        jsonObject("cycleMinutes", 5),
                        "트레이딩 주기"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parameterKey").value("trading.window"))
                .andExpect(jsonPath("$.data.version").value(0));

        mockMvc.perform(get("/api/admin/system-parameters")
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].parameterKey").value("trading.window"));

        mockMvc.perform(patch("/api/admin/system-parameters/{parameterKey}", "trading.window")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new SystemParameterService.UpdateSystemParameterRequest(
                        jsonObject("cycleMinutes", 3),
                        "수정된 주기",
                        0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.valueJson.cycleMinutes").value(3));

        mockMvc.perform(patch("/api/admin/system-parameters/{parameterKey}", "trading.window")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new SystemParameterService.UpdateSystemParameterRequest(
                        jsonObject("cycleMinutes", 1),
                        "stale",
                        0L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OPTIMISTIC_LOCK_CONFLICT"))
                .andExpect(jsonPath("$.meta.currentVersion").value(1));

        mockMvc.perform(delete("/api/admin/system-parameters/{parameterKey}", "trading.window")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }
}
