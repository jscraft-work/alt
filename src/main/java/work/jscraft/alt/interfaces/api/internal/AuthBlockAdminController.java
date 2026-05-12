package work.jscraft.alt.interfaces.api.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.auth.application.AuthService;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.auth.security.TrustedProxyIpResolver;
import work.jscraft.alt.interfaces.api.admin.AuthController;

@Validated
@RestController
@RequestMapping("/api/admin/auth")
public class AuthBlockAdminController {

    private final AuthService authService;
    private final TrustedProxyIpResolver trustedProxyIpResolver;

    public AuthBlockAdminController(AuthService authService, TrustedProxyIpResolver trustedProxyIpResolver) {
        this.authService = authService;
        this.trustedProxyIpResolver = trustedProxyIpResolver;
    }

    @PostMapping("/login-blocks/release")
    public AuthController.ApiDataResponse<AuthController.SuccessResponse> releaseLoginBlock(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @Valid @RequestBody ReleaseLoginBlockRequest request,
            jakarta.servlet.http.HttpServletRequest httpServletRequest) {
        authService.releaseBlock(principal, trustedProxyIpResolver.resolve(httpServletRequest), request.clientIp());
        return new AuthController.ApiDataResponse<>(new AuthController.SuccessResponse(true));
    }

    public record ReleaseLoginBlockRequest(@NotBlank String clientIp) {
    }
}
