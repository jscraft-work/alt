package work.jscraft.alt.auth.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import work.jscraft.alt.auth.config.AuthProperties;

@Component
public class JwtSessionAuthenticationFilter extends OncePerRequestFilter {

    private final JwtSessionService jwtSessionService;
    private final AuthProperties authProperties;
    private final AuthCookieFactory authCookieFactory;

    public JwtSessionAuthenticationFilter(
            JwtSessionService jwtSessionService,
            AuthProperties authProperties,
            AuthCookieFactory authCookieFactory) {
        this.jwtSessionService = jwtSessionService;
        this.authProperties = authProperties;
        this.authCookieFactory = authCookieFactory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        AdminSessionPrincipal principal;
        try {
            principal = jwtSessionService.parse(token);
            if (jwtSessionService.isAbsoluteSessionExpired(principal)) {
                response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.expireSessionCookie().toString());
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.expireSessionCookie().toString());
            filterChain.doFilter(request, response);
            return;
        }

        AbstractAuthenticationToken authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.roleCode())));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (shouldRefresh(request, response, principal)) {
                IssuedAdminSession refreshed = jwtSessionService.refresh(principal);
                response.addHeader(HttpHeaders.SET_COOKIE,
                        authCookieFactory.createSessionCookie(refreshed.token(), authProperties.getIdleTimeout()).toString());
            }
            SecurityContextHolder.clearContext();
        }
    }

    private boolean shouldRefresh(HttpServletRequest request, HttpServletResponse response, AdminSessionPrincipal principal) {
        return !"/api/auth/logout".equals(request.getRequestURI())
                && response.getStatus() < 400
                && jwtSessionService.shouldRefresh(principal);
    }

    private String resolveToken(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, authProperties.getSessionCookieName());
        return cookie == null ? null : cookie.getValue();
    }
}
