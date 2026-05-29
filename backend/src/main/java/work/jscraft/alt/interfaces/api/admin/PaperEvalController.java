package work.jscraft.alt.interfaces.api.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.trading.application.ops.SetupMetricService;
import work.jscraft.alt.trading.application.ops.SetupMetricService.DailyPnlPoint;
import work.jscraft.alt.trading.application.ops.SetupMetricService.MetricSnapshot;
import work.jscraft.alt.trading.application.ops.SetupMetricService.RecentMatchView;

/**
 * 운영자 paper-eval API — 박스 단타 v1 인스턴스의 4 지표 + 시계열.
 *
 * <p>D-day +7 시연 시 운영자가 호출:
 * <ul>
 *   <li>{@code GET /api/admin/paper-eval/{instanceId}?lookback=30} — 직전 30 건 4 지표 + 비용 wall</li>
 *   <li>{@code GET /api/admin/paper-eval/{instanceId}/series?days=30} — 일별 net_pnl_pct 시계열</li>
 * </ul>
 *
 * <p>admin 인증 가드는 SecurityConfiguration 의 {@code /api/admin/**} 패턴에서 자동 적용.
 */
@RestController
@RequestMapping("/api/admin/paper-eval")
public class PaperEvalController {

    private static final int DEFAULT_LOOKBACK = 30;
    private static final int DEFAULT_SERIES_DAYS = 30;
    private static final int DEFAULT_RECENT_MATCHES_LIMIT = 30;
    private static final int MAX_RECENT_MATCHES_LIMIT = 200;

    private final SetupMetricService setupMetricService;

    public PaperEvalController(SetupMetricService setupMetricService) {
        this.setupMetricService = setupMetricService;
    }

    @GetMapping("/{instanceId}")
    public ApiDataResponse<MetricSnapshot> evaluate(
            @PathVariable UUID instanceId,
            @RequestParam(name = "lookback", required = false) Integer lookback) {
        int effectiveLookback = lookback != null && lookback > 0 ? lookback : DEFAULT_LOOKBACK;
        MetricSnapshot snapshot = setupMetricService.computeRecent(instanceId, effectiveLookback);
        return new ApiDataResponse<>(snapshot);
    }

    @GetMapping("/{instanceId}/series")
    public ApiDataResponse<List<DailyPnlPoint>> series(
            @PathVariable UUID instanceId,
            @RequestParam(name = "days", required = false) Integer days) {
        int effectiveDays = days != null && days > 0 ? days : DEFAULT_SERIES_DAYS;
        List<DailyPnlPoint> points = setupMetricService.computeDailySeries(instanceId, effectiveDays);
        return new ApiDataResponse<>(points);
    }

    @GetMapping("/{instanceId}/recent-matches")
    public ApiDataResponse<List<RecentMatchView>> recentMatches(
            @PathVariable UUID instanceId,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int requested = limit != null && limit > 0 ? limit : DEFAULT_RECENT_MATCHES_LIMIT;
        int effectiveLimit = Math.min(requested, MAX_RECENT_MATCHES_LIMIT);
        List<RecentMatchView> matches = setupMetricService.findRecentMatches(instanceId, effectiveLimit);
        return new ApiDataResponse<>(matches);
    }
}
