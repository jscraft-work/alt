package work.jscraft.alt.interfaces.api.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.auth.application.AuthService;
import work.jscraft.alt.auth.application.AuthService.LoginResult;
import work.jscraft.alt.auth.config.AuthProperties;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.auth.security.AuthCookieFactory;
import work.jscraft.alt.auth.security.TrustedProxyIpResolver;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory authCookieFactory;
    private final CsrfTokenRepository csrfTokenRepository;
    private final AuthProperties authProperties;
    private final TrustedProxyIpResolver trustedProxyIpResolver;

    public AuthController(
            AuthService authService,
            AuthCookieFactory authCookieFactory,
            CsrfTokenRepository csrfTokenRepository,
            AuthProperties authProperties,
            TrustedProxyIpResolver trustedProxyIpResolver) {
        this.authService = authService;
        this.authCookieFactory = authCookieFactory;
        this.csrfTokenRepository = csrfTokenRepository;
        this.authProperties = authProperties;
        this.trustedProxyIpResolver = trustedProxyIpResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiDataResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        LoginResult result = authService.login(
                request.loginId(),
                request.password(),
                trustedProxyIpResolver.resolve(httpServletRequest));
        issueCsrfToken(httpServletRequest, httpServletResponse);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                authCookieFactory.createSessionCookie(result.session().token(), authProperties.getIdleTimeout()).toString());

        LoginResponse payload = new LoginResponse(result.expiresAt().toString(), authService.toUserView(result.principal()));

        return ResponseEntity.ok()
                .headers(headers)
                .body(new ApiDataResponse<>(payload));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiDataResponse<SuccessResponse>> logout(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(principal, trustedProxyIpResolver.resolve(request));
        csrfTokenRepository.saveToken(null, request, response);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, authCookieFactory.expireSessionCookie().toString());

        return ResponseEntity.ok()
                .headers(headers)
                .body(new ApiDataResponse<>(new SuccessResponse(true)));
    }

    @GetMapping("/me")
    public ApiDataResponse<AuthService.UserView> me(@AuthenticationPrincipal AdminSessionPrincipal principal) {
        return new ApiDataResponse<>(authService.toUserView(principal));
    }

    @GetMapping("/csrf")
    public ApiDataResponse<SuccessResponse> csrf(HttpServletRequest request, HttpServletResponse response) {
        issueCsrfToken(request, response);
        return new ApiDataResponse<>(new SuccessResponse(true));
    }

    private CsrfToken issueCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(csrfToken, request, response);
        return csrfToken;
    }

    public record LoginRequest(
            @NotBlank String loginId,
            @NotBlank String password) {
    }

    public record LoginResponse(
            String expiresAt,
            AuthService.UserView user) {
    }

    public record SuccessResponse(boolean success) {
    }

    public record ApiDataResponse<T>(T data) {
    }
}
