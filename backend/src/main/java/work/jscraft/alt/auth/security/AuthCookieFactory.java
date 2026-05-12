package work.jscraft.alt.auth.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import work.jscraft.alt.auth.config.AuthProperties;

@Component
public class AuthCookieFactory {

    private final AuthProperties authProperties;

    public AuthCookieFactory(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public ResponseCookie createSessionCookie(String token, Duration maxAge) {
        return ResponseCookie.from(authProperties.getSessionCookieName(), token)
                .httpOnly(true)
                .secure(authProperties.isSecureCookie())
                .sameSite(authProperties.getSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie expireSessionCookie() {
        return ResponseCookie.from(authProperties.getSessionCookieName(), "")
                .httpOnly(true)
                .secure(authProperties.isSecureCookie())
                .sameSite(authProperties.getSameSite())
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
