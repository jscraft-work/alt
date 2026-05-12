package work.jscraft.alt.interfaces.api.admin;

import java.util.List;
import java.util.UUID;

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

import work.jscraft.alt.strategy.application.StrategyTemplateService;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.common.dto.ApiSuccessResponse;

@Validated
@RestController
@RequestMapping("/api/admin/strategy-templates")
public class StrategyTemplateAdminController {

    private final StrategyTemplateService strategyTemplateService;

    public StrategyTemplateAdminController(StrategyTemplateService strategyTemplateService) {
        this.strategyTemplateService = strategyTemplateService;
    }

    @GetMapping
    public ApiDataResponse<List<StrategyTemplateService.StrategyTemplateView>> list() {
        return new ApiDataResponse<>(strategyTemplateService.listStrategyTemplates());
    }

    @PostMapping
    public ApiDataResponse<StrategyTemplateService.StrategyTemplateView> create(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @Valid @RequestBody StrategyTemplateService.CreateStrategyTemplateRequest request) {
        return new ApiDataResponse<>(strategyTemplateService.createStrategyTemplate(request, principal));
    }

    @PatchMapping("/{strategyTemplateId}")
    public ApiDataResponse<StrategyTemplateService.StrategyTemplateView> update(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID strategyTemplateId,
            @Valid @RequestBody StrategyTemplateService.UpdateStrategyTemplateRequest request) {
        return new ApiDataResponse<>(strategyTemplateService.updateStrategyTemplate(strategyTemplateId, request, principal));
    }

    @DeleteMapping("/{strategyTemplateId}")
    public ApiDataResponse<ApiSuccessResponse> delete(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID strategyTemplateId) {
        strategyTemplateService.deleteStrategyTemplate(strategyTemplateId, principal);
        return new ApiDataResponse<>(new ApiSuccessResponse(true));
    }
}
