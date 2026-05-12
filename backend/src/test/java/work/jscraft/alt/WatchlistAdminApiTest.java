package work.jscraft.alt;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.strategy.application.StrategyInstanceService;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WatchlistAdminApiTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void addDeleteAndReAddWatchlistAsset() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("Watchlist Template", "template prompt", modelProfile);
        AssetMasterEntity assetMaster = createAsset("005930", "삼성전자", "00126380");
        JsonNode createdInstance = createStrategyInstance(
                adminLogin,
                template,
                "Watchlist Instance",
                "paper",
                null,
                jsonObject("scope", "full_watchlist"));
        String strategyInstanceId = createdInstance.path("id").asText();

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/watchlist", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.AddWatchlistAssetRequest(assetMaster.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetMasterId").value(assetMaster.getId().toString()))
                .andExpect(jsonPath("$.data.symbolCode").value("005930"))
                .andExpect(jsonPath("$.data.dartCorpCode").value("00126380"));

        mockMvc.perform(get("/api/admin/strategy-instances/{strategyInstanceId}/watchlist", strategyInstanceId)
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].assetMasterId").value(assetMaster.getId().toString()));

        mockMvc.perform(delete("/api/admin/strategy-instances/{strategyInstanceId}/watchlist/{assetMasterId}",
                strategyInstanceId, assetMaster.getId())
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(get("/api/admin/strategy-instances/{strategyInstanceId}/watchlist", strategyInstanceId)
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/watchlist", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new StrategyInstanceService.AddWatchlistAssetRequest(assetMaster.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetMasterId").value(assetMaster.getId().toString()));
    }
}
