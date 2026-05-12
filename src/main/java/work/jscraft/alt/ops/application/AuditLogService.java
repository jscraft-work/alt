package work.jscraft.alt.ops.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.ops.infrastructure.persistence.AuditLogEntity;
import work.jscraft.alt.ops.infrastructure.persistence.AuditLogRepository;

@Service
public class AuditLogService {

    private static final String ACTOR_TYPE_APP_USER = "APP_USER";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void recordCreate(
            AdminSessionPrincipal actor,
            String targetType,
            UUID targetId,
            String actionType,
            Object after,
            Map<String, Object> summary) {
        save(actor, targetType, targetId, actionType, null, after, summary);
    }

    public void recordUpdate(
            AdminSessionPrincipal actor,
            String targetType,
            UUID targetId,
            String actionType,
            Object before,
            Object after,
            Map<String, Object> summary) {
        save(actor, targetType, targetId, actionType, before, after, summary);
    }

    public void recordDelete(
            AdminSessionPrincipal actor,
            String targetType,
            UUID targetId,
            String actionType,
            Object before,
            Map<String, Object> summary) {
        save(actor, targetType, targetId, actionType, before, null, summary);
    }

    private void save(
            AdminSessionPrincipal actor,
            String targetType,
            UUID targetId,
            String actionType,
            Object before,
            Object after,
            Map<String, Object> summary) {
        OffsetDateTime occurredAt = OffsetDateTime.now(clock);

        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setOccurredAt(occurredAt);
        auditLog.setBusinessDate(occurredAt.toLocalDate());
        auditLog.setActorType(ACTOR_TYPE_APP_USER);
        auditLog.setActorId(actor.userId());
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setActionType(actionType);
        auditLog.setBeforeJson(before == null ? null : objectMapper.valueToTree(before));
        auditLog.setAfterJson(after == null ? null : objectMapper.valueToTree(after));
        auditLog.setSummaryJson(summary == null ? null : objectMapper.valueToTree(summary));
        auditLogRepository.save(auditLog);
    }
}
