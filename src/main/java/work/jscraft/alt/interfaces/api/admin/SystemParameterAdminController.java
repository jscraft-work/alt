package work.jscraft.alt.interfaces.api.admin;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.ops.application.SystemParameterService;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.common.dto.ApiSuccessResponse;

@Validated
@RestController
@RequestMapping("/api/admin/system-parameters")
public class SystemParameterAdminController {

    private final SystemParameterService systemParameterService;

    public SystemParameterAdminController(SystemParameterService systemParameterService) {
        this.systemParameterService = systemParameterService;
    }

    @GetMapping
    public ApiDataResponse<List<SystemParameterService.SystemParameterView>> list() {
        return new ApiDataResponse<>(systemParameterService.listSystemParameters());
    }

    @PostMapping
    public ApiDataResponse<SystemParameterService.SystemParameterView> create(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @Valid @RequestBody SystemParameterService.CreateSystemParameterRequest request) {
        return new ApiDataResponse<>(systemParameterService.createSystemParameter(request, principal));
    }

    @PatchMapping("/{parameterKey}")
    public ApiDataResponse<SystemParameterService.SystemParameterView> update(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable String parameterKey,
            @Valid @RequestBody SystemParameterService.UpdateSystemParameterRequest request) {
        return new ApiDataResponse<>(systemParameterService.updateSystemParameter(parameterKey, request, principal));
    }

    @DeleteMapping("/{parameterKey}")
    public ApiDataResponse<ApiSuccessResponse> delete(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable String parameterKey) {
        systemParameterService.deleteSystemParameter(parameterKey, principal);
        return new ApiDataResponse<>(new ApiSuccessResponse(true));
    }
}
