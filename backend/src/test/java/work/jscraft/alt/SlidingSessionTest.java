package work.jscraft.alt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SlidingSessionTest extends AuthApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetState();
        createAdminUser();
    }

    @Test
    void meRefreshesSessionWhenRemainingLifetimeIsLow() throws Exception {
        LoginCookies loginCookies = login();
        var originalSession = jwtSessionService.parse(loginCookies.sessionCookie().getValue());

        mutableClock.advanceSeconds(6 * 60 * 60 + 60);

        MvcResult result = mockMvc.perform(get("/api/auth/me")
                .cookie(loginCookies.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn();

        var refreshedCookie = result.getResponse().getCookie(authProperties.getSessionCookieName());
        assertThat(refreshedCookie).isNotNull();

        var refreshedSession = jwtSessionService.parse(refreshedCookie.getValue());
        assertThat(refreshedSession.sessionStartedAt()).isEqualTo(originalSession.sessionStartedAt());
        assertThat(refreshedSession.expiresAt()).isAfter(originalSession.expiresAt());
    }
}
