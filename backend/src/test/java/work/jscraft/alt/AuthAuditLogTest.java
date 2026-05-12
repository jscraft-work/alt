package work.jscraft.alt;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAuditLogTest extends AuthApiIntegrationTestSupport {

    @BeforeEach
    void setUp() {
        resetState();
        createAdminUser();
    }

    @Test
    void loginSuccessFailureAndLogoutArePersistedToAuditLog() throws Exception {
        LoginCookies loginCookies = login("198.51.100.30");

        mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr("198.51.100.31"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new LoginRequest("admin", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout")
                .with(remoteAddr("198.51.100.30"))
                .cookie(loginCookies.sessionCookie(), loginCookies.csrfCookie())
                .header(authProperties.getCsrfHeaderName(), loginCookies.csrfCookie().getValue()))
                .andExpect(status().isOk());

        List<String> actionTypes = auditLogRepository.findAll(Sort.by("occurredAt")).stream()
                .sorted(Comparator.comparing(auditLog -> auditLog.getOccurredAt().toInstant()))
                .map(auditLog -> auditLog.getActionType()
                        + ":"
                        + auditLog.getSummaryJson().get("clientIp").asText()
                        + ":"
                        + auditLog.getSummaryJson().get("result").asText())
                .toList();

        assertThat(actionTypes).containsExactly(
                "AUTH_LOGIN_SUCCESS:198.51.100.30:SUCCESS",
                "AUTH_LOGIN_FAILURE:198.51.100.31:FAILURE",
                "AUTH_LOGOUT:198.51.100.30:SUCCESS");
    }
}
