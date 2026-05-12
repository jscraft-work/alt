package work.jscraft.alt.auth.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.auth.infrastructure.persistence.AppUserEntity;
import work.jscraft.alt.auth.infrastructure.persistence.AppUserRepository;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.auth.security.IssuedAdminSession;
import work.jscraft.alt.auth.security.JwtSessionService;
import work.jscraft.alt.auth.security.RedisLoginBlockService;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtSessionService jwtSessionService;
    private final RedisLoginBlockService redisLoginBlockService;
    private final AuthAuditService authAuditService;
    private final Clock clock;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtSessionService jwtSessionService,
            RedisLoginBlockService redisLoginBlockService,
            AuthAuditService authAuditService,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtSessionService = jwtSessionService;
        this.redisLoginBlockService = redisLoginBlockService;
        this.authAuditService = authAuditService;
        this.clock = clock;
    }

    @Transactional
    public LoginResult login(String loginId, String password, String clientIp) {
        redisLoginBlockService.assertNotBlocked(clientIp);

        AppUserEntity appUser = appUserRepository.findByLoginId(loginId).orElse(null);

        if (appUser == null || !appUser.isEnabled() || !passwordEncoder.matches(password, appUser.getPasswordHash())) {
            RedisLoginBlockService.FailureResult failureResult = redisLoginBlockService.recordFailure(clientIp);
            authAuditService.recordLoginFailure(loginId, appUser, clientIp, failureResult.failureCount());
            if (failureResult.blocked()) {
                authAuditService.recordLoginBlocked(loginId, clientIp);
                throw blocked();
            }
            throw invalidCredentials();
        }

        redisLoginBlockService.resetFailures(clientIp);
        appUser.setLastLoginAt(OffsetDateTime.now(clock));
        IssuedAdminSession issuedSession = jwtSessionService.issueSession(appUser);
        authAuditService.recordLoginSuccess(appUser, clientIp);

        return new LoginResult(
                jwtSessionService.toPrincipal(appUser),
                issuedSession,
                OffsetDateTime.ofInstant(issuedSession.expiresAt(), clock.getZone()));
    }

    public void logout(AdminSessionPrincipal principal, String clientIp) {
        authAuditService.recordLogout(principal, clientIp);
    }

    public void releaseBlock(AdminSessionPrincipal principal, String actorClientIp, String releasedClientIp) {
        redisLoginBlockService.releaseBlock(releasedClientIp);
        authAuditService.recordBlockReleased(principal, actorClientIp, releasedClientIp);
    }

    public UserView toUserView(AdminSessionPrincipal principal) {
        return new UserView(
                principal.userId().toString(),
                principal.loginId(),
                principal.displayName(),
                principal.roleCode());
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    private ResponseStatusException blocked() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 일시적으로 차단되었습니다.");
    }

    public record LoginResult(
            AdminSessionPrincipal principal,
            IssuedAdminSession session,
            OffsetDateTime expiresAt) {
    }

    public record UserView(
            String id,
            String loginId,
            String displayName,
            String roleCode) {
    }
}
