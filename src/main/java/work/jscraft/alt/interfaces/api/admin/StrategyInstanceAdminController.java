package work.jscraft.alt.interfaces.api.admin;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.strategy.application.StrategyInstanceService;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.common.dto.ApiSuccessResponse;

@Validated
@RestController
@RequestMapping("/api/admin/strategy-instances")
public class StrategyInstanceAdminController {

    private final StrategyInstanceService strategyInstanceService;

    public StrategyInstanceAdminController(StrategyInstanceService strategyInstanceService) {
        this.strategyInstanceService = strategyInstanceService;
    }

    @GetMapping
    public ApiDataResponse<List<StrategyInstanceService.StrategyInstanceView>> list(
            @RequestParam(required = false) @Pattern(
                    regexp = StrategyInstanceService.LIFECYCLE_STATE_PATTERN,
                    message = "lifecycleState가 올바르지 않습니다.") String lifecycleState,
            @RequestParam(required = false) @Pattern(
                    regexp = StrategyInstanceService.EXECUTION_MODE_PATTERN,
                    message = "executionMode가 올바르지 않습니다.") String executionMode) {
        return new ApiDataResponse<>(strategyInstanceService.listStrategyInstances(lifecycleState, executionMode));
    }

    @PostMapping
    public ApiDataResponse<StrategyInstanceService.StrategyInstanceView> create(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @Valid @RequestBody StrategyInstanceService.CreateStrategyInstanceRequest request) {
        return new ApiDataResponse<>(strategyInstanceService.createStrategyInstance(request, principal));
    }

    @PatchMapping("/{strategyInstanceId}")
    public ApiDataResponse<StrategyInstanceService.StrategyInstanceView> update(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID strategyInstanceId,
            @Valid @RequestBody StrategyInstanceService.UpdateStrategyInstanceRequest request) {
        return new ApiDataResponse<>(strategyInstanceService.updateStrategyInstance(strategyInstanceId, request, principal));
    }

    @PostMapping("/{strategyInstanceId}/lifecycle")
    public ApiDataResponse<StrategyInstanceService.StrategyInstanceView> changeLifecycle(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID strategyInstanceId,
            @Valid @RequestBody StrategyInstanceService.LifecycleChangeRequest request) {
        return new ApiDataResponse<>(strategyInstanceService.changeLifecycle(strategyInstanceId, request, principal));
    }

    @GetMapping("/{strategyInstanceId}/prompt-versions")
    public ApiDataResponse<List<StrategyInstanceService.PromptVersionView>> listPromptVersions(
            @PathVariable UUID strategyInstanceId) {
        return new ApiDataResponse<>(strategyInstanceService.listPromptVersions(strategyInstanceId));
    }

    @PostMapping("/{strategyInstanceId}/prompt-versions")
    public ApiDataResponse<StrategyInstanceService.PromptVersionView> createPromptVersion(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID strategyInstanceId,
            @Valid @RequestBody StrategyInstanceService.CreatePromptVersionRequest request) {
        return new ApiDataResponse<>(strategyInstanceService.createPromptVersion(strategyInstanceId, request, principal));
    }

    @PostMapping("/{strategyInstanceId}/prompt-versions/{promptVersionId}/activate")
    public ApiDataResponse<StrategyInstanceService.PromptVersionView> activatePromptVersion(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID strategyInstanceId,
            @PathVariable UUID promptVersionId,
            @Valid @RequestBody StrategyInstanceService.ActivatePromptVersionRequest request) {
        return new ApiDataResponse<>(
                strategyInstanceService.restorePromptVersion(strategyInstanceId, promptVersionId, request, principal));
    }

    @GetMapping("/{strategyInstanceId}/watchlist")
    public ApiDataResponse<List<StrategyInstanceService.WatchlistAssetView>> listWatchlist(
            @PathVariable UUID strategyInstanceId) {
        return new ApiDataResponse<>(strategyInstanceService.listWatchlist(strategyInstanceId));
    }

    @PostMapping("/{strategyInstanceId}/watchlist")
    public ApiDataResponse<StrategyInstanceService.WatchlistAssetView> addWatchlistAsset(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID strategyInstanceId,
            @Valid @RequestBody StrategyInstanceService.AddWatchlistAssetRequest request) {
        return new ApiDataResponse<>(strategyInstanceService.addWatchlistAsset(strategyInstanceId, request, principal));
    }

    @DeleteMapping("/{strategyInstanceId}/watchlist/{assetMasterId}")
    public ApiDataResponse<ApiSuccessResponse> removeWatchlistAsset(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID strategyInstanceId,
            @PathVariable UUID assetMasterId) {
        strategyInstanceService.removeWatchlistAsset(strategyInstanceId, assetMasterId, principal);
        return new ApiDataResponse<>(new ApiSuccessResponse(true));
    }
}
