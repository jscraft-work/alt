package work.jscraft.alt.ops.application;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.common.error.ApiConflictException;
import work.jscraft.alt.common.error.OptimisticLockConflictException;
import work.jscraft.alt.ops.infrastructure.persistence.SystemParameterEntity;
import work.jscraft.alt.ops.infrastructure.persistence.SystemParameterRepository;

@Service
@Transactional(readOnly = true)
public class SystemParameterService {

    private static final Sort UPDATED_AT_DESC = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final String TARGET_TYPE = "SYSTEM_PARAMETER";

    private final SystemParameterRepository systemParameterRepository;
    private final AuditLogService auditLogService;

    public SystemParameterService(SystemParameterRepository systemParameterRepository, AuditLogService auditLogService) {
        this.systemParameterRepository = systemParameterRepository;
        this.auditLogService = auditLogService;
    }

    public List<SystemParameterView> listSystemParameters() {
        return systemParameterRepository.findAll(UPDATED_AT_DESC).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public SystemParameterView createSystemParameter(CreateSystemParameterRequest request, AdminSessionPrincipal actor) {
        if (systemParameterRepository.existsById(request.parameterKey())) {
            throw new ApiConflictException("REQUEST_CONFLICT", "이미 존재하는 시스템 파라미터입니다.");
        }

        SystemParameterEntity entity = new SystemParameterEntity();
        entity.setParameterKey(request.parameterKey());
        entity.setValueJson(request.valueJson());
        entity.setDescription(request.description());
        SystemParameterView created = toView(systemParameterRepository.saveAndFlush(entity));
        auditLogService.recordCreate(
                actor,
                TARGET_TYPE,
                null,
                "SYSTEM_PARAMETER_CREATED",
                created,
                Map.of("parameterKey", created.parameterKey()));
        return created;
    }

    @Transactional
    public SystemParameterView updateSystemParameter(
            String parameterKey,
            UpdateSystemParameterRequest request,
            AdminSessionPrincipal actor) {
        SystemParameterEntity entity = findSystemParameter(parameterKey);
        assertVersion(request.version(), entity.getVersion());
        SystemParameterView before = toView(entity);
        entity.setValueJson(request.valueJson());
        entity.setDescription(request.description());
        SystemParameterView updated = toView(systemParameterRepository.saveAndFlush(entity));
        auditLogService.recordUpdate(
                actor,
                TARGET_TYPE,
                null,
                "SYSTEM_PARAMETER_UPDATED",
                before,
                updated,
                Map.of("parameterKey", updated.parameterKey()));
        return updated;
    }

    @Transactional
    public void deleteSystemParameter(String parameterKey, AdminSessionPrincipal actor) {
        SystemParameterEntity entity = findSystemParameter(parameterKey);
        SystemParameterView before = toView(entity);
        systemParameterRepository.delete(entity);
        systemParameterRepository.flush();
        auditLogService.recordDelete(
                actor,
                TARGET_TYPE,
                null,
                "SYSTEM_PARAMETER_DELETED",
                before,
                Map.of("parameterKey", before.parameterKey()));
    }

    private SystemParameterEntity findSystemParameter(String parameterKey) {
        return systemParameterRepository.findById(parameterKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "시스템 파라미터를 찾을 수 없습니다."));
    }

    private void assertVersion(long requestedVersion, long currentVersion) {
        if (requestedVersion != currentVersion) {
            throw new OptimisticLockConflictException(currentVersion);
        }
    }

    private SystemParameterView toView(SystemParameterEntity entity) {
        return new SystemParameterView(
                entity.getParameterKey(),
                entity.getValueJson(),
                entity.getDescription(),
                entity.getVersion(),
                entity.getUpdatedAt().toString());
    }

    public record SystemParameterView(
            String parameterKey,
            JsonNode valueJson,
            String description,
            long version,
            String updatedAt) {
    }

    public record CreateSystemParameterRequest(
            @NotBlank(message = "parameterKey는 필수입니다.") @Size(max = 120, message = "parameterKey는 120자 이하여야 합니다.") String parameterKey,
            @NotNull(message = "valueJson은 필수입니다.") JsonNode valueJson,
            String description) {
    }

    public record UpdateSystemParameterRequest(
            @NotNull(message = "valueJson은 필수입니다.") JsonNode valueJson,
            String description,
            @NotNull(message = "version은 필수입니다.") Long version) {
    }
}
