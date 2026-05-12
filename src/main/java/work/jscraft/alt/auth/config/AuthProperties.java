package work.jscraft.alt.auth.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    @NotBlank
    @Size(min = 32)
    private String jwtSecret = "change-this-jwt-secret-change-this-jwt-secret";

    @NotNull
    private Duration idleTimeout = Duration.ofHours(8);

    @NotNull
    private Duration absoluteTimeout = Duration.ofDays(7);

    @NotNull
    private Duration slidingRefreshThreshold = Duration.ofHours(2);

    @NotBlank
    private String sessionCookieName = "ALT_ADMIN_SESSION";

    @NotBlank
    private String csrfCookieName = "XSRF-TOKEN";

    @NotBlank
    private String csrfHeaderName = "X-CSRF-TOKEN";

    private boolean secureCookie = true;

    @NotBlank
    private String sameSite = "Lax";

    private String trustedProxyCidrs = "";

    private int loginFailureLimit = 5;

    @NotNull
    private Duration loginBlockDuration = Duration.ofMinutes(5);

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Duration getAbsoluteTimeout() {
        return absoluteTimeout;
    }

    public void setAbsoluteTimeout(Duration absoluteTimeout) {
        this.absoluteTimeout = absoluteTimeout;
    }

    public Duration getSlidingRefreshThreshold() {
        return slidingRefreshThreshold;
    }

    public void setSlidingRefreshThreshold(Duration slidingRefreshThreshold) {
        this.slidingRefreshThreshold = slidingRefreshThreshold;
    }

    public String getSessionCookieName() {
        return sessionCookieName;
    }

    public void setSessionCookieName(String sessionCookieName) {
        this.sessionCookieName = sessionCookieName;
    }

    public String getCsrfCookieName() {
        return csrfCookieName;
    }

    public void setCsrfCookieName(String csrfCookieName) {
        this.csrfCookieName = csrfCookieName;
    }

    public String getCsrfHeaderName() {
        return csrfHeaderName;
    }

    public void setCsrfHeaderName(String csrfHeaderName) {
        this.csrfHeaderName = csrfHeaderName;
    }

    public boolean isSecureCookie() {
        return secureCookie;
    }

    public void setSecureCookie(boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    public String getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    public void setTrustedProxyCidrs(String trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs;
    }

    public int getLoginFailureLimit() {
        return loginFailureLimit;
    }

    public void setLoginFailureLimit(int loginFailureLimit) {
        this.loginFailureLimit = loginFailureLimit;
    }

    public Duration getLoginBlockDuration() {
        return loginBlockDuration;
    }

    public void setLoginBlockDuration(Duration loginBlockDuration) {
        this.loginBlockDuration = loginBlockDuration;
    }
}
