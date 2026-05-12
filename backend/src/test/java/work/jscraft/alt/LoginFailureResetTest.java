package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginFailureResetTest extends AuthApiIntegrationTestSupport {

    private static final String CLIENT_IP = "203.0.113.11";

    @BeforeEach
    void setUp() {
        resetState();
        createAdminUser();
    }

    @Test
    void successfulLoginClearsFailureCounter() throws Exception {
        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr(CLIENT_IP))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new LoginRequest("admin", "wrong-password"))))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(redisLoginBlockService.getFailureCount(CLIENT_IP)).isEqualTo(2);

        mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr(CLIENT_IP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new LoginRequest("admin", "Password!123"))))
                .andExpect(status().isOk());

        assertThat(redisLoginBlockService.getFailureCount(CLIENT_IP)).isZero();
        assertThat(redisLoginBlockService.isBlocked(CLIENT_IP)).isFalse();

        mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr(CLIENT_IP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new LoginRequest("admin", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        assertThat(redisLoginBlockService.getFailureCount(CLIENT_IP)).isEqualTo(1);
    }
}
