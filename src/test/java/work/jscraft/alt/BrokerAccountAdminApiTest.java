package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import work.jscraft.alt.strategy.application.BrokerAccountService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerAccountAdminApiTest extends AdminCatalogApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        createAdminUser();
    }

    @Test
    void brokerAccountsEnforceUniquenessAndKeepMaskedField() throws Exception {
        LoginCookies adminLogin = login();

        BrokerAccountService.CreateBrokerAccountRequest createRequest =
                new BrokerAccountService.CreateBrokerAccountRequest(
                        "KIS",
                        "1234567890",
                        "메인 계좌",
                        "1234-****-7890");

        String createdBody = mockMvc.perform(post("/api/admin/broker-accounts")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountMasked").value("1234-****-7890"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String brokerAccountId = objectMapper.readTree(createdBody).path("data").path("id").asText();
        assertThat(createdBody).doesNotContain("1234567890");

        mockMvc.perform(post("/api/admin/broker-accounts")
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(createRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_CONFLICT"));

        mockMvc.perform(patch("/api/admin/broker-accounts/{brokerAccountId}", brokerAccountId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new BrokerAccountService.UpdateBrokerAccountRequest(
                        "KIS",
                        "1234567890",
                        "서브 계좌",
                        "1234-****-7890",
                        0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountAlias").value("서브 계좌"))
                .andExpect(jsonPath("$.data.accountMasked").value("1234-****-7890"))
                .andExpect(jsonPath("$.data.version").value(1));

        String listBody = mockMvc.perform(get("/api/admin/broker-accounts")
                .cookie(adminLogin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(brokerAccountId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(listBody).contains("1234-****-7890");
        assertThat(listBody).doesNotContain("1234567890");

        mockMvc.perform(delete("/api/admin/broker-accounts/{brokerAccountId}", brokerAccountId)
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }
}
