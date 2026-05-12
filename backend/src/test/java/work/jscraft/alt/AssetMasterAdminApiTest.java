package work.jscraft.alt;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.marketdata.application.AssetMasterService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssetMasterAdminApiTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void assetMasterSupportsCrudAndSoftDelete() throws Exception {
        LoginCookies adminLogin = login();

        String createdBody = mockMvc.perform(post("/api/admin/assets")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new AssetMasterService.CreateAssetMasterRequest(
                        "005930",
                        "삼성전자",
                        "KOSPI",
                        "00126380",
                        false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbolCode").value("005930"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createdBody).path("data");
        String assetId = created.path("id").asText();

        mockMvc.perform(patch("/api/admin/assets/{assetMasterId}", assetId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new AssetMasterService.UpdateAssetMasterRequest(
                        "005930",
                        "삼성전자",
                        "KOSPI",
                        "00126380",
                        true,
                        0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(true))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get("/api/admin/assets")
                .cookie(adminLogin.sessionCookie())
                .param("q", "5930")
                .param("hidden", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(assetId));

        mockMvc.perform(delete("/api/admin/assets/{assetMasterId}", assetId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(get("/api/admin/assets")
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        Integer deletedCount = jdbcTemplate.queryForObject(
                "select count(*) from asset_master where id = ? and deleted_at is not null",
                Integer.class,
                UUID.fromString(assetId));
        assertThat(deletedCount).isEqualTo(1);
    }
}
