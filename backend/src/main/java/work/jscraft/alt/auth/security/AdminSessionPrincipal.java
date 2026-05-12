package work.jscraft.alt.auth.security;

import java.time.Instant;
import java.util.UUID;

public record AdminSessionPrincipal(
        UUID userId,
        String loginId,
        String displayName,
        String roleCode,
        Instant sessionStartedAt,
        Instant expiresAt) {
}
