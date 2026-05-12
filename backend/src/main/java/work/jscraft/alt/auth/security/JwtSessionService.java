package work.jscraft.alt.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import work.jscraft.alt.auth.config.AuthProperties;
import work.jscraft.alt.auth.infrastructure.persistence.AppUserEntity;

@Service
public class JwtSessionService {

    private static final String CLAIM_LOGIN_ID = "login_id";
    private static final String CLAIM_DISPLAY_NAME = "display_name";
    private static final String CLAIM_ROLE_CODE = "role_code";
    private static final String CLAIM_SESSION_STARTED_AT = "session_started_at";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AuthProperties authProperties;
    private final Clock clock;

    public JwtSessionService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, AuthProperties authProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    public IssuedAdminSession issueSession(AppUserEntity appUser) {
        return issueSession(toPrincipal(appUser), Instant.now(clock), Instant.now(clock));
    }

    public IssuedAdminSession issueSession(AdminSessionPrincipal principal, Instant sessionStartedAt, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(authProperties.getIdleTimeout());

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .subject(principal.userId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(CLAIM_LOGIN_ID, principal.loginId())
                .claim(CLAIM_DISPLAY_NAME, principal.displayName())
                .claim(CLAIM_ROLE_CODE, principal.roleCode())
                .claim(CLAIM_SESSION_STARTED_AT, sessionStartedAt.toString())
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256)
                        .type("JWT")
                        .build(),
                claimsSet)).getTokenValue();

        return new IssuedAdminSession(token, expiresAt, sessionStartedAt);
    }

    public AdminSessionPrincipal parse(String token) {
        Jwt jwt = jwtDecoder.decode(token);

        Instant sessionStartedAt = Instant.parse(jwt.getClaimAsString(CLAIM_SESSION_STARTED_AT));
        Instant expiresAt = jwt.getExpiresAt();

        if (expiresAt == null) {
            throw new IllegalArgumentException("JWT exp claim is required");
        }

        return new AdminSessionPrincipal(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(CLAIM_LOGIN_ID),
                jwt.getClaimAsString(CLAIM_DISPLAY_NAME),
                jwt.getClaimAsString(CLAIM_ROLE_CODE),
                sessionStartedAt,
                expiresAt);
    }

    public boolean isAbsoluteSessionExpired(AdminSessionPrincipal principal) {
        Instant maxExpiresAt = principal.sessionStartedAt().plus(authProperties.getAbsoluteTimeout());
        return Instant.now(clock).isAfter(maxExpiresAt);
    }

    public boolean shouldRefresh(AdminSessionPrincipal principal) {
        Duration remaining = Duration.between(Instant.now(clock), principal.expiresAt());
        return !remaining.isNegative() && !remaining.isZero()
                && remaining.compareTo(authProperties.getSlidingRefreshThreshold()) <= 0;
    }

    public IssuedAdminSession refresh(AdminSessionPrincipal principal) {
        return issueSession(principal, principal.sessionStartedAt(), Instant.now(clock));
    }

    public AdminSessionPrincipal toPrincipal(AppUserEntity appUser) {
        Instant issuedAt = Instant.now(clock);
        return new AdminSessionPrincipal(
                appUser.getId(),
                appUser.getLoginId(),
                appUser.getDisplayName(),
                appUser.getRoleCode(),
                issuedAt,
                issuedAt.plus(authProperties.getIdleTimeout()));
    }

}
