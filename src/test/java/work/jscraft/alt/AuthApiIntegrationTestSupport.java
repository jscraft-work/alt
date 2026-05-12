package work.jscraft.alt;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.Cookie;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import work.jscraft.alt.auth.config.AuthProperties;
import work.jscraft.alt.auth.infrastructure.persistence.AppUserEntity;
import work.jscraft.alt.auth.infrastructure.persistence.AppUserRepository;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.auth.security.IssuedAdminSession;
import work.jscraft.alt.auth.security.JwtSessionService;
import work.jscraft.alt.auth.security.RedisLoginBlockService;
import work.jscraft.alt.ops.infrastructure.persistence.AuditLogRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ PostgreSqlTestConfiguration.class, RedisTestConfiguration.class, AuthTestClockConfiguration.class })
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.auth.jwt-secret=test-jwt-secret-test-jwt-secret-test-jwt-secret",
        "app.seed.enabled=false"
})
abstract class AuthApiIntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired
    protected MutableClock mutableClock;

    @Autowired
    protected JwtSessionService jwtSessionService;

    @Autowired
    protected AuthProperties authProperties;

    @Autowired
    protected RedisLoginBlockService redisLoginBlockService;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    protected void resetState() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_log, app_user CASCADE");
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        mutableClock.setInstant(AuthTestClockConfiguration.DEFAULT_INSTANT);
    }

    protected AppUserEntity createAdminUser() {
        return createUser("admin", "Password!123", "관리자", "ADMIN");
    }

    protected AppUserEntity createUser(String loginId, String password, String displayName, String roleCode) {
        AppUserEntity appUser = new AppUserEntity();
        appUser.setLoginId(loginId);
        appUser.setPasswordHash(passwordEncoder.encode(password));
        appUser.setDisplayName(displayName);
        appUser.setRoleCode(roleCode);
        appUser.setEnabled(true);
        return appUserRepository.saveAndFlush(appUser);
    }

    protected String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected LoginCookies login() throws Exception {
        return login("admin", "Password!123", "127.0.0.1");
    }

    protected LoginCookies login(String clientIp) throws Exception {
        return login("admin", "Password!123", clientIp);
    }

    protected LoginCookies login(String loginId, String password, String clientIp) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .with(remoteAddr(clientIp))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(toJson(new LoginRequest(loginId, password))))
                .andReturn();

        Cookie sessionCookie = result.getResponse().getCookie(authProperties.getSessionCookieName());
        Cookie csrfCookie = result.getResponse().getCookie(authProperties.getCsrfCookieName());

        return new LoginCookies(
                sessionCookie,
                csrfCookie,
                getSetCookieHeader(result, authProperties.getSessionCookieName()),
                getSetCookieHeader(result, authProperties.getCsrfCookieName()));
    }

    protected RequestPostProcessor remoteAddr(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }

    protected String getSetCookieHeader(MvcResult result, String cookieName) {
        List<String> values = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return values.stream()
                .filter(value -> value.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow();
    }

    protected Cookie sessionCookieFor(AppUserEntity appUser, Instant sessionStartedAt, Instant issuedAt) {
        AdminSessionPrincipal principal = new AdminSessionPrincipal(
                appUser.getId(),
                appUser.getLoginId(),
                appUser.getDisplayName(),
                appUser.getRoleCode(),
                sessionStartedAt,
                issuedAt.plus(authProperties.getIdleTimeout()));
        IssuedAdminSession session = jwtSessionService.issueSession(principal, sessionStartedAt, issuedAt);

        Cookie cookie = new Cookie(authProperties.getSessionCookieName(), session.token());
        cookie.setHttpOnly(true);
        cookie.setSecure(authProperties.isSecureCookie());
        cookie.setPath("/");
        return cookie;
    }

    protected record LoginRequest(String loginId, String password) {
    }

    protected record LoginCookies(
            Cookie sessionCookie,
            Cookie csrfCookie,
            String sessionSetCookie,
            String csrfSetCookie) {
    }
}
