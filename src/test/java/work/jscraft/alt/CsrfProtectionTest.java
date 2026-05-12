package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CsrfProtectionTest extends AuthApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetState();
        createAdminUser();
    }

    @Test
    void logoutWithoutCsrfHeaderIsRejected() throws Exception {
        LoginCookies loginCookies = login();

        mockMvc.perform(post("/api/auth/logout")
                .cookie(loginCookies.sessionCookie(), loginCookies.csrfCookie()))
                .andExpect(status().isForbidden());
    }
}
