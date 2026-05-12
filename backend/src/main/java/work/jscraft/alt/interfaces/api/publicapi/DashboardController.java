package work.jscraft.alt.interfaces.api.publicapi;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.dashboard.application.DashboardQueryService;
import work.jscraft.alt.dashboard.application.DashboardViews.InstanceDashboardView;
import work.jscraft.alt.dashboard.application.DashboardViews.StrategyOverviewCardView;
import work.jscraft.alt.dashboard.application.DashboardViews.SystemStatusEntryView;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;

    public DashboardController(DashboardQueryService dashboardQueryService) {
        this.dashboardQueryService = dashboardQueryService;
    }

    @GetMapping("/strategy-overview")
    public ApiDataResponse<List<StrategyOverviewCardView>> strategyOverview(
            @RequestParam(required = false) String lifecycleState,
            @RequestParam(required = false) Boolean autoPaused) {
        return new ApiDataResponse<>(dashboardQueryService.listOverviewCards(lifecycleState, autoPaused));
    }

    @GetMapping("/instances/{strategyInstanceId}")
    public ApiDataResponse<InstanceDashboardView> instanceDashboard(@PathVariable UUID strategyInstanceId) {
        return new ApiDataResponse<>(dashboardQueryService.getInstanceDashboard(strategyInstanceId));
    }

    @GetMapping("/system-status")
    public ApiDataResponse<List<SystemStatusEntryView>> systemStatus() {
        return new ApiDataResponse<>(dashboardQueryService.listSystemStatus());
    }
}
