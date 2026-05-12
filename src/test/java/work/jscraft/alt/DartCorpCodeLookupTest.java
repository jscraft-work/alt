package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DartCorpCodeLookupTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void lookupReturnsConfiguredCodeAndAllowsNullWhenMissing() throws Exception {
        LoginCookies adminLogin = login();
        createAsset("005930", "삼성전자", "00126380");
        createAsset("035420", "NAVER", null);

        mockMvc.perform(get("/api/admin/assets/dart-corp-code-lookup")
                .queryParam("symbolCode", "005930")
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbolCode").value("005930"))
                .andExpect(jsonPath("$.data.symbolName").value("삼성전자"))
                .andExpect(jsonPath("$.data.dartCorpCode").value("00126380"));

        mockMvc.perform(get("/api/admin/assets/dart-corp-code-lookup")
                .queryParam("symbolCode", "035420")
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbolCode").value("035420"))
                .andExpect(jsonPath("$.data.dartCorpCode").isEmpty());

        mockMvc.perform(get("/api/admin/assets/dart-corp-code-lookup")
                .queryParam("symbolCode", "999999")
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
