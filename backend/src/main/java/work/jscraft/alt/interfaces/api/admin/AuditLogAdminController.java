package work.jscraft.alt.interfaces.api.admin;

import java.time.OffsetDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.ops.application.AuditLogQueryService;
import work.jscraft.alt.ops.application.AuditLogQueryService.AuditLogFilter;
import work.jscraft.alt.ops.application.AuditLogQueryService.AuditLogPageResult;

/**
 * audit_log read-only 페이지네이션 + 필터 (F4).
 */
@RestController
@RequestMapping("/api/admin/audit-log")
public class AuditLogAdminController {

    private final AuditLogQueryService service;

    public AuditLogAdminController(AuditLogQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiDataResponse<AuditLogPageResult> list(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "50") int size,
            @RequestParam(name = "from", required = false) OffsetDateTime from,
            @RequestParam(name = "to", required = false) OffsetDateTime to,
            @RequestParam(name = "targetType", required = false) String targetType,
            @RequestParam(name = "actorType", required = false) String actorType,
            @RequestParam(name = "actionType", required = false) String actionType) {
        AuditLogFilter filter = new AuditLogFilter();
        filter.page = page;
        filter.size = size;
        filter.from = from;
        filter.to = to;
        filter.targetType = targetType;
        filter.actorType = actorType;
        filter.actionType = actionType;
        return new ApiDataResponse<>(service.findFiltered(filter));
    }
}
