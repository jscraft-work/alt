package work.jscraft.alt.auth.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.auth.infrastructure.persistence.AppUserEntity;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.ops.infrastructure.persistence.AuditLogEntity;
import work.jscraft.alt.ops.infrastructure.persistence.AuditLogRepository;

@Service
public class AuthAuditService {

    private static final String ACTOR_TYPE_ANONYMOUS = "ANONYMOUS";
    private static final String ACTOR_TYPE_APP_USER = "APP_USER";
    private static final String TARGET_TYPE_APP_USER = "APP_USER";
    private static final String TARGET_TYPE_AUTH = "AUTH";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILURE = "FAILURE";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuthAuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginSuccess(AppUserEntity appUser, String clientIp) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("clientIp", clientIp);
        summary.put("loginId", appUser.getLoginId());
        summary.put("result", RESULT_SUCCESS);

        save(
                ACTOR_TYPE_APP_USER,
                appUser.getId(),
                TARGET_TYPE_APP_USER,
                appUser.getId(),
                "AUTH_LOGIN_SUCCESS",
                summary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(String attemptedLoginId, AppUserEntity targetUser, String clientIp, int failureCount) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("clientIp", clientIp);
        summary.put("loginId", attemptedLoginId);
        summary.put("result", RESULT_FAILURE);
        summary.put("failureCount", failureCount);

        save(
                ACTOR_TYPE_ANONYMOUS,
                null,
                TARGET_TYPE_APP_USER,
                targetUser == null ? null : targetUser.getId(),
                "AUTH_LOGIN_FAILURE",
                summary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginBlocked(String attemptedLoginId, String clientIp) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("clientIp", clientIp);
        summary.put("loginId", attemptedLoginId);
        summary.put("result", RESULT_FAILURE);

        save(
                ACTOR_TYPE_ANONYMOUS,
                null,
                TARGET_TYPE_AUTH,
                null,
                "AUTH_LOGIN_BLOCKED",
                summary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogout(AdminSessionPrincipal principal, String clientIp) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("clientIp", clientIp);
        summary.put("loginId", principal.loginId());
        summary.put("result", RESULT_SUCCESS);

        save(
                ACTOR_TYPE_APP_USER,
                principal.userId(),
                TARGET_TYPE_APP_USER,
                principal.userId(),
                "AUTH_LOGOUT",
                summary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBlockReleased(AdminSessionPrincipal principal, String actorClientIp, String releasedClientIp) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("clientIp", actorClientIp);
        summary.put("releasedClientIp", releasedClientIp);
        summary.put("loginId", principal.loginId());
        summary.put("result", RESULT_SUCCESS);

        save(
                ACTOR_TYPE_APP_USER,
                principal.userId(),
                TARGET_TYPE_AUTH,
                null,
                "AUTH_LOGIN_BLOCK_RELEASED",
                summary);
    }

    private void save(
            String actorType,
            UUID actorId,
            String targetType,
            UUID targetId,
            String actionType,
            ObjectNode summaryJson) {
        OffsetDateTime occurredAt = OffsetDateTime.now(clock);

        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setOccurredAt(occurredAt);
        auditLog.setBusinessDate(occurredAt.toLocalDate());
        auditLog.setActorType(actorType);
        auditLog.setActorId(actorId);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setActionType(actionType);
        auditLog.setSummaryJson(summaryJson);
        auditLogRepository.save(auditLog);
    }
}
