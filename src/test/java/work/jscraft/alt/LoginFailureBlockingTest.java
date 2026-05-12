package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginFailureBlockingTest extends AuthApiIntegrationTestSupport {

    private static final String CLIENT_IP = "203.0.113.10";

    @BeforeEach
    void setUp() {
        resetState();
        createAdminUser();
    }

    @Test
    void fifthFailureBlocksIpForFiveMinutes() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr(CLIENT_IP))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new LoginRequest("admin", "wrong-password"))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr(CLIENT_IP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new LoginRequest("admin", "wrong-password"))))
                .andExpect(status().isTooManyRequests());

        assertThat(redisLoginBlockService.isBlocked(CLIENT_IP)).isTrue();
        assertThat(redisLoginBlockService.getFailureCount(CLIENT_IP)).isEqualTo(5);
        assertThat(redisLoginBlockService.getBlockTtl(CLIENT_IP).toSeconds()).isBetween(1L, 300L);

        mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr(CLIENT_IP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new LoginRequest("admin", "Password!123"))))
                .andExpect(status().isTooManyRequests());
    }
}
