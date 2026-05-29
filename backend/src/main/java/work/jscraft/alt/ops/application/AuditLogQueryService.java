package work.jscraft.alt.ops.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.ops.infrastructure.persistence.AuditLogEntity;
import work.jscraft.alt.ops.infrastructure.persistence.AuditLogRepository;

/**
 * audit_log 페이지네이션 + 필터 read-only 조회 (F4).
 */
@Service
public class AuditLogQueryService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final OffsetDateTime SENTINEL_MIN_TIME =
            OffsetDateTime.parse("1900-01-01T00:00:00+00:00");
    private static final OffsetDateTime SENTINEL_MAX_TIME =
            OffsetDateTime.parse("9999-12-31T23:59:59+00:00");

    private final AuditLogRepository auditLogRepository;

    public AuditLogQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AuditLogPageResult findFiltered(AuditLogFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter 가 null 입니다");
        }
        Pageable pageable = PageRequest.of(
                Math.max(0, filter.page),
                Math.max(1, Math.min(filter.size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "occurredAt"));

        OffsetDateTime effectiveFrom = filter.from != null ? filter.from : SENTINEL_MIN_TIME;
        OffsetDateTime effectiveTo = filter.to != null ? filter.to : SENTINEL_MAX_TIME;
        String targetType = blankSentinel(filter.targetType);
        String actorType = blankSentinel(filter.actorType);
        String actionType = blankSentinel(filter.actionType);

        Page<AuditLogEntity> page = auditLogRepository.findFiltered(
                effectiveFrom, effectiveTo, targetType, actorType, actionType, pageable);

        List<AuditLogRow> rows = new ArrayList<>(page.getNumberOfElements());
        for (AuditLogEntity e : page.getContent()) {
            rows.add(new AuditLogRow(
                    e.getId(),
                    e.getOccurredAt(),
                    e.getActorType(),
                    e.getActorId(),
                    e.getTargetType(),
                    e.getTargetId(),
                    e.getActionType(),
                    e.getBeforeJson() == null ? null : e.getBeforeJson().toString(),
                    e.getAfterJson() == null ? null : e.getAfterJson().toString(),
                    e.getSummaryJson() == null ? null : e.getSummaryJson().toString()));
        }
        return new AuditLogPageResult(
                rows, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static String blankSentinel(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.isEmpty() ? "" : t;
    }

    public static final class AuditLogFilter {
        public int page = 0;
        public int size = 50;
        public OffsetDateTime from;
        public OffsetDateTime to;
        public String targetType;
        public String actorType;
        public String actionType;
    }

    public record AuditLogPageResult(
            List<AuditLogRow> rows,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record AuditLogRow(
            UUID id,
            OffsetDateTime occurredAt,
            String actorType,
            UUID actorId,
            String targetType,
            UUID targetId,
            String actionType,
            String beforeJson,
            String afterJson,
            String summaryJson) {
    }
}
