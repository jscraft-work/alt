package work.jscraft.alt.interfaces.api.admin;

import java.time.OffsetDateTime;
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
import work.jscraft.alt.trading.application.ops.SetupMetricService.TradeHistoryFilter;
import work.jscraft.alt.trading.application.ops.SetupMetricService.TradeHistoryResult;

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

    /**
     * trade-history 페이지네이션 + 필터 조회 (F3).
     *
     * <p>예: {@code /trade-history?page=0&size=50&from=2026-05-01T00:00:00%2B09:00&to=2026-06-01T00:00:00%2B09:00&symbol=005930&winOnly=true&sort=net_pnl_pct:desc}
     *
     * @param sort one of {@code "exit_time:desc"}, {@code "exit_time:asc"},
     *             {@code "net_pnl_pct:desc"}, {@code "net_pnl_pct:asc"},
     *             {@code "gross_pnl_pct:desc"}, {@code "gross_pnl_pct:asc"}
     */
    @GetMapping("/{instanceId}/trade-history")
    public ApiDataResponse<TradeHistoryResult> tradeHistory(
            @PathVariable UUID instanceId,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "50") int size,
            @RequestParam(name = "from", required = false) OffsetDateTime from,
            @RequestParam(name = "to", required = false) OffsetDateTime to,
            @RequestParam(name = "symbol", required = false) String symbol,
            @RequestParam(name = "winOnly", required = false, defaultValue = "false") boolean winOnly,
            @RequestParam(name = "lossOnly", required = false, defaultValue = "false") boolean lossOnly,
            @RequestParam(name = "sort", required = false) String sort) {
        TradeHistoryFilter filter = new TradeHistoryFilter();
        filter.page = page;
        filter.size = size;
        filter.from = from;
        filter.to = to;
        filter.symbol = symbol;
        filter.winOnly = winOnly;
        filter.lossOnly = lossOnly;
        filter.sort = sort;
        TradeHistoryResult result = setupMetricService.findTradeHistory(instanceId, filter);
        return new ApiDataResponse<>(result);
    }
}
