package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthLoginApiTest extends AuthApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetState();
        createAdminUser();
    }

    @Test
    void loginIssuesJwtAndCsrfCookies() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(toJson(new LoginRequest("admin", "Password!123"))))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();

        assertThat(result.getResponse().getCookie(authProperties.getSessionCookieName())).isNotNull();
        assertThat(result.getResponse().getCookie(authProperties.getSessionCookieName()).isHttpOnly()).isTrue();
        assertThat(result.getResponse().getCookie(authProperties.getSessionCookieName()).getSecure()).isTrue();
        assertThat(getSetCookieHeader(result, authProperties.getSessionCookieName())).contains("SameSite=Lax");

        assertThat(result.getResponse().getCookie(authProperties.getCsrfCookieName())).isNotNull();
        assertThat(result.getResponse().getCookie(authProperties.getCsrfCookieName()).isHttpOnly()).isFalse();
        assertThat(result.getResponse().getCookie(authProperties.getCsrfCookieName()).getSecure()).isTrue();

        assertThat(HttpStatus.OK.value()).isEqualTo(result.getResponse().getStatus());
        assertThat(body).contains("\"loginId\":\"admin\"");
        assertThat(body).contains("\"displayName\":\"관리자\"");
        assertThat(body).contains("\"roleCode\":\"ADMIN\"");
        assertThat(body).contains("\"expiresAt\"");
    }
}
