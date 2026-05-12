package work.jscraft.alt;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicReadAuthBoundaryTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
    }

    @Test
    void publicReadEndpointsAllowAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/trade-orders"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/trade-decisions"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/news"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/disclosures"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/charts/minutes")
                        .param("symbolCode", "005930")
                        .param("date", "2026-05-11"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/charts/order-overlays")
                        .param("symbolCode", "005930")
                        .param("date", "2026-05-11"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/strategy-overview"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/strategy-instances"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/strategy-templates"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/broker-accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chartParamsAreRequired() throws Exception {
        mockMvc.perform(get("/api/charts/minutes"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/charts/order-overlays"))
                .andExpect(status().is4xxClientError());
    }
}
