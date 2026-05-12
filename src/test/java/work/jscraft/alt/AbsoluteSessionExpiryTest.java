package work.jscraft.alt;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AbsoluteSessionExpiryTest extends AuthApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetState();
    }

    @Test
    void meReturnsUnauthorizedWhenAbsoluteSessionLifetimeIsExceeded() throws Exception {
        var appUser = createAdminUser();
        Instant now = mutableClock.instant();
        Instant sessionStartedAt = now.minus(authProperties.getAbsoluteTimeout()).minusSeconds(1);
        Instant issuedAt = now.minusSeconds(60);

        var response = mockMvc.perform(get("/api/auth/me")
                .cookie(sessionCookieFor(appUser, sessionStartedAt, issuedAt)))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse();

        assertThat(response.getCookie(authProperties.getSessionCookieName())).isNotNull();
        assertThat(response.getCookie(authProperties.getSessionCookieName()).getMaxAge()).isZero();
    }
}
