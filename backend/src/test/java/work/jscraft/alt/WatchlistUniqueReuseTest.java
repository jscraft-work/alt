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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WatchlistUniqueReuseTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void softDeletedWatchlistAssetCanBeRegisteredAgain() throws Exception {
        LoginCookies adminLogin = login();
        LlmModelProfileEntity modelProfile = createTradingModelProfile();
        StrategyTemplateEntity template = createStrategyTemplate("Watchlist Unique Template", "template prompt", modelProfile);
        AssetMasterEntity assetMaster = createAsset("000660", "SK하이닉스", "00164779");
        JsonNode createdInstance = createStrategyInstance(adminLogin, template, "Watchlist Reuse", "paper", null, null);
        String strategyInstanceId = createdInstance.path("id").asText();

        StrategyInstanceService.AddWatchlistAssetRequest request =
                new StrategyInstanceService.AddWatchlistAssetRequest(assetMaster.getId());

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/watchlist", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/watchlist", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_CONFLICT"));

        mockMvc.perform(delete("/api/admin/strategy-instances/{strategyInstanceId}/watchlist/{assetMasterId}",
                strategyInstanceId, assetMaster.getId())
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/strategy-instances/{strategyInstanceId}/watchlist", strategyInstanceId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
                .andExpect(status().isOk());

        Long totalRows = jdbcTemplate.queryForObject(
                "select count(*) from strategy_instance_watchlist_relation where strategy_instance_id = ? and asset_master_id = ?",
                Long.class,
                java.util.UUID.fromString(strategyInstanceId),
                assetMaster.getId());
        Long liveRows = jdbcTemplate.queryForObject(
                """
                select count(*) from strategy_instance_watchlist_relation
                where strategy_instance_id = ? and asset_master_id = ? and deleted_at is null
                """,
                Long.class,
                java.util.UUID.fromString(strategyInstanceId),
                assetMaster.getId());

        org.assertj.core.api.Assertions.assertThat(totalRows).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(liveRows).isEqualTo(1L);
    }
}
