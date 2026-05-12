package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthMeApiTest extends AuthApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetState();
        createAdminUser();
    }

    @Test
    void meReturnsCurrentUserForValidSession() throws Exception {
        LoginCookies loginCookies = login();

        String body = mockMvc.perform(get("/api/auth/me")
                .cookie(loginCookies.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"loginId\":\"admin\"");
        assertThat(body).contains("\"displayName\":\"관리자\"");
        assertThat(body).contains("\"roleCode\":\"ADMIN\"");
    }
}
