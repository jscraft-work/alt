package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthBlockReleaseApiTest extends AuthApiIntegrationTestSupport {

    private static final String BLOCKED_IP = "203.0.113.15";

    @BeforeEach
    void setUp() {
        resetState();
        createAdminUser();
    }

    @Test
    void adminCanReleaseBlockedIp() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr(BLOCKED_IP))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new LoginRequest("admin", "wrong-password"))))
                    .andExpect(attempt == 5 ? status().isTooManyRequests() : status().isUnauthorized());
        }

        assertThat(redisLoginBlockService.isBlocked(BLOCKED_IP)).isTrue();

        LoginCookies adminLogin = login("198.51.100.40");

        mockMvc.perform(post("/api/admin/auth/login-blocks/release")
                .with(remoteAddr("198.51.100.41"))
                .cookie(adminLogin.sessionCookie(), adminLogin.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), adminLogin.csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ReleaseLoginBlockRequest(BLOCKED_IP))))
                .andExpect(status().isOk());

        assertThat(redisLoginBlockService.isBlocked(BLOCKED_IP)).isFalse();
        assertThat(redisLoginBlockService.getFailureCount(BLOCKED_IP)).isZero();

        mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr(BLOCKED_IP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new LoginRequest("admin", "Password!123"))))
                .andExpect(status().isOk());
    }

    private record ReleaseLoginBlockRequest(String clientIp) {
    }
}
