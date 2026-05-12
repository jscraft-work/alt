package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthLogoutApiTest extends AuthApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetState();
        createAdminUser();
    }

    @Test
    void logoutExpiresSessionCookie() throws Exception {
        LoginCookies loginCookies = login();

        var response = mockMvc.perform(post("/api/auth/logout")
                .cookie(loginCookies.sessionCookie(), loginCookies.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), loginCookies.csrfCookie().getValue()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        assertThat(response.getContentAsString()).contains("\"success\":true");
        assertThat(response.getCookie(authProperties.getSessionCookieName())).isNotNull();
        assertThat(response.getCookie(authProperties.getSessionCookieName()).getMaxAge()).isZero();
        assertThat(response.getCookie(authProperties.getCsrfCookieName())).isNotNull();
        assertThat(response.getCookie(authProperties.getCsrfCookieName()).getMaxAge()).isZero();
    }
}
