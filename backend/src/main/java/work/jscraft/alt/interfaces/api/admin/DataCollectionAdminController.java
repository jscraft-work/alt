package work.jscraft.alt.interfaces.api.admin;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.ops.application.CollectionDashboardService;
import work.jscraft.alt.ops.application.CollectionDashboardService.CollectionSummary;
import work.jscraft.alt.ops.application.CollectionDashboardService.OpsEventView;
import work.jscraft.alt.ops.application.CollectionDashboardService.SubscriptionListView;

/**
 * 운영자 data-collection 모니터링 + KIS WS 종목 manual subscribe CRUD.
 *
 * <p>인증 가드는 SecurityConfiguration 의 {@code /api/admin/**} 패턴에서 자동 적용.
 */
@Validated
@RestController
@RequestMapping("/api/admin/data-collection")
public class DataCollectionAdminController {

    private static final int DEFAULT_OPS_EVENT_LIMIT = 10;

    private final CollectionDashboardService service;

    public DataCollectionAdminController(CollectionDashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiDataResponse<CollectionSummary> summary() {
        return new ApiDataResponse<>(service.summary());
    }

    @GetMapping("/ws/subscriptions")
    public ApiDataResponse<SubscriptionListView> wsSubscriptions() {
        return new ApiDataResponse<>(service.listSubscriptions());
    }

    @PostMapping("/ws/subscribe")
    public ApiDataResponse<Map<String, Object>> wsSubscribe(
            @Valid @RequestBody WsSubscribeRequest request) {
        long added = service.subscribeAdhoc(request.symbolCodes());
        return new ApiDataResponse<>(Map.of(
                "addedCount", added,
                "requestedCount", request.symbolCodes().size()));
    }

    @DeleteMapping("/ws/subscribe/{symbolCode}")
    public ApiDataResponse<Map<String, Object>> wsUnsubscribe(
            @PathVariable String symbolCode) {
        boolean removed = service.unsubscribeAdhoc(symbolCode);
        return new ApiDataResponse<>(Map.of(
                "removed", removed,
                "symbolCode", symbolCode));
    }

    @GetMapping("/ops-events")
    public ApiDataResponse<List<OpsEventView>> opsEvents(
            @RequestParam("service") String serviceName,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int effective = limit != null && limit > 0 ? limit : DEFAULT_OPS_EVENT_LIMIT;
        return new ApiDataResponse<>(service.recentOpsEvents(serviceName, effective));
    }

    public record WsSubscribeRequest(
            @NotNull
            @NotEmpty
            List<String> symbolCodes) {
    }
}
