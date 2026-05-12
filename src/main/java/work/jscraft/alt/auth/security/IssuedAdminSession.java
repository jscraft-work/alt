package work.jscraft.alt.auth.security;

import java.time.Instant;

public record IssuedAdminSession(
        String token,
        Instant expiresAt,
        Instant sessionStartedAt) {
}
