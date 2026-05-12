package work.jscraft.alt.llm.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.common.error.OptimisticLockConflictException;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.ops.application.AuditLogService;

@Service
@Transactional(readOnly = true)
public class LlmModelProfileService {

    public static final String MODEL_PURPOSE_PATTERN = "trading_decision|daily_report|news_assessment";

    private static final Sort UPDATED_AT_DESC = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final String TARGET_TYPE = "LLM_MODEL_PROFILE";

    private final LlmModelProfileRepository llmModelProfileRepository;
    private final AuditLogService auditLogService;

    public LlmModelProfileService(LlmModelProfileRepository llmModelProfileRepository, AuditLogService auditLogService) {
        this.llmModelProfileRepository = llmModelProfileRepository;
        this.auditLogService = auditLogService;
    }

    public List<ModelProfileView> listModelProfiles(String purpose, Boolean enabled) {
        return llmModelProfileRepository.findAll(UPDATED_AT_DESC).stream()
                .filter(entity -> purpose == null || entity.getPurpose().equals(purpose))
                .filter(entity -> enabled == null || entity.isEnabled() == enabled.booleanValue())
                .map(this::toView)
                .toList();
    }

    @Transactional
    public ModelProfileView createModelProfile(CreateModelProfileRequest request, AdminSessionPrincipal actor) {
        LlmModelProfileEntity entity = new LlmModelProfileEntity();
        apply(entity, request);
        ModelProfileView created = toView(llmModelProfileRepository.saveAndFlush(entity));
        auditLogService.recordCreate(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "MODEL_PROFILE_CREATED",
                created,
                Map.of("purpose", created.purpose(), "modelName", created.modelName()));
        return created;
    }

    @Transactional
    public ModelProfileView updateModelProfile(UUID modelProfileId, UpdateModelProfileRequest request, AdminSessionPrincipal actor) {
        LlmModelProfileEntity entity = findModelProfile(modelProfileId);
        assertVersion(request.version(), entity.getVersion());
        ModelProfileView before = toView(entity);
        apply(entity, request);
        ModelProfileView updated = toView(llmModelProfileRepository.saveAndFlush(entity));
        auditLogService.recordUpdate(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "MODEL_PROFILE_UPDATED",
                before,
                updated,
                Map.of("purpose", updated.purpose(), "modelName", updated.modelName()));
        return updated;
    }

    @Transactional
    public void deleteModelProfile(UUID modelProfileId, AdminSessionPrincipal actor) {
        LlmModelProfileEntity entity = findModelProfile(modelProfileId);
        ModelProfileView before = toView(entity);
        llmModelProfileRepository.delete(entity);
        llmModelProfileRepository.flush();
        auditLogService.recordDelete(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "MODEL_PROFILE_DELETED",
                before,
                Map.of("purpose", before.purpose(), "modelName", before.modelName()));
    }

    private void apply(LlmModelProfileEntity entity, ModelProfileMutation request) {
        entity.setPurpose(request.purpose());
        entity.setProvider(request.provider());
        entity.setModelName(request.modelName());
        entity.setEnabled(request.enabled());
    }

    private LlmModelProfileEntity findModelProfile(UUID modelProfileId) {
        return llmModelProfileRepository.findById(modelProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "모델 프로필을 찾을 수 없습니다."));
    }

    private void assertVersion(long requestedVersion, long currentVersion) {
        if (requestedVersion != currentVersion) {
            throw new OptimisticLockConflictException(currentVersion);
        }
    }

    private ModelProfileView toView(LlmModelProfileEntity entity) {
        return new ModelProfileView(
                entity.getId().toString(),
                entity.getPurpose(),
                entity.getProvider(),
                entity.getModelName(),
                entity.isEnabled(),
                entity.getVersion(),
                entity.getUpdatedAt().toString());
    }

    public sealed interface ModelProfileMutation permits CreateModelProfileRequest, UpdateModelProfileRequest {
        String purpose();

        String provider();

        String modelName();

        boolean enabled();
    }

    public record ModelProfileView(
            String id,
            String purpose,
            String provider,
            String modelName,
            boolean enabled,
            long version,
            String updatedAt) {
    }

    public record CreateModelProfileRequest(
            @NotBlank(message = "purpose는 필수입니다.") @Pattern(regexp = MODEL_PURPOSE_PATTERN, message = "purpose가 올바르지 않습니다.") String purpose,
            @NotBlank(message = "provider는 필수입니다.") @Size(max = 40, message = "provider는 40자 이하여야 합니다.") String provider,
            @NotBlank(message = "modelName은 필수입니다.") @Size(max = 120, message = "modelName은 120자 이하여야 합니다.") String modelName,
            boolean enabled)
            implements ModelProfileMutation {
    }

    public record UpdateModelProfileRequest(
            @NotBlank(message = "purpose는 필수입니다.") @Pattern(regexp = MODEL_PURPOSE_PATTERN, message = "purpose가 올바르지 않습니다.") String purpose,
            @NotBlank(message = "provider는 필수입니다.") @Size(max = 40, message = "provider는 40자 이하여야 합니다.") String provider,
            @NotBlank(message = "modelName은 필수입니다.") @Size(max = 120, message = "modelName은 120자 이하여야 합니다.") String modelName,
            boolean enabled,
            @NotNull(message = "version은 필수입니다.") Long version)
            implements ModelProfileMutation {
    }
}
