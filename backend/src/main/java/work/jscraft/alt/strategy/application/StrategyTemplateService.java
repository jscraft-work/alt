package work.jscraft.alt.strategy.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateRepository;

@Service
@Transactional(readOnly = true)
public class StrategyTemplateService {

    private static final Sort UPDATED_AT_DESC = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final String TARGET_TYPE = "STRATEGY_TEMPLATE";

    private final StrategyTemplateRepository strategyTemplateRepository;
    private final StrategyInstanceRepository strategyInstanceRepository;
    private final LlmModelProfileRepository llmModelProfileRepository;
    private final AuditLogService auditLogService;

    public StrategyTemplateService(
            StrategyTemplateRepository strategyTemplateRepository,
            StrategyInstanceRepository strategyInstanceRepository,
            LlmModelProfileRepository llmModelProfileRepository,
            AuditLogService auditLogService) {
        this.strategyTemplateRepository = strategyTemplateRepository;
        this.strategyInstanceRepository = strategyInstanceRepository;
        this.llmModelProfileRepository = llmModelProfileRepository;
        this.auditLogService = auditLogService;
    }

    public List<StrategyTemplateView> listStrategyTemplates() {
        return strategyTemplateRepository.findAll(UPDATED_AT_DESC).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public StrategyTemplateView createStrategyTemplate(CreateStrategyTemplateRequest request, AdminSessionPrincipal actor) {
        StrategyTemplateEntity entity = new StrategyTemplateEntity();
        apply(entity, request);
        StrategyTemplateView created = toView(strategyTemplateRepository.saveAndFlush(entity));
        auditLogService.recordCreate(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "STRATEGY_TEMPLATE_CREATED",
                created,
                Map.of("name", created.name()));
        return created;
    }

    @Transactional
    public StrategyTemplateView updateStrategyTemplate(
            UUID strategyTemplateId,
            UpdateStrategyTemplateRequest request,
            AdminSessionPrincipal actor) {
        StrategyTemplateEntity entity = findStrategyTemplate(strategyTemplateId);
        assertVersion(request.version(), entity.getVersion());
        StrategyTemplateView before = toView(entity);
        int previousCycleMinutes = entity.getDefaultCycleMinutes();
        apply(entity, request);
        StrategyTemplateView updated = toView(strategyTemplateRepository.saveAndFlush(entity));
        if (previousCycleMinutes != updated.defaultCycleMinutes()) {
            strategyInstanceRepository.markScheduleDirtyByStrategyTemplateIdAndCycleMinutesIsNull(entity.getId());
        }
        auditLogService.recordUpdate(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "STRATEGY_TEMPLATE_UPDATED",
                before,
                updated,
                Map.of("name", updated.name()));
        return updated;
    }

    @Transactional
    public void deleteStrategyTemplate(UUID strategyTemplateId, AdminSessionPrincipal actor) {
        StrategyTemplateEntity entity = findStrategyTemplate(strategyTemplateId);
        StrategyTemplateView before = toView(entity);
        strategyTemplateRepository.delete(entity);
        strategyTemplateRepository.flush();
        auditLogService.recordDelete(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "STRATEGY_TEMPLATE_DELETED",
                before,
                Map.of("name", before.name()));
    }

    private void apply(StrategyTemplateEntity entity, StrategyTemplateMutation request) {
        LlmModelProfileEntity modelProfile = llmModelProfileRepository.findById(request.defaultTradingModelProfileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "모델 프로필을 찾을 수 없습니다."));
        if (!"trading_decision".equals(modelProfile.getPurpose())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기본 트레이딩 모델은 trading_decision 용도여야 합니다.");
        }

        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setDefaultCycleMinutes(request.defaultCycleMinutes());
        entity.setDefaultPromptText(request.defaultPromptText());
        entity.setDefaultExecutionConfigJson(request.defaultExecutionConfig());
        entity.setDefaultTradingModelProfile(modelProfile);
    }

    private StrategyTemplateEntity findStrategyTemplate(UUID strategyTemplateId) {
        return strategyTemplateRepository.findById(strategyTemplateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "전략 템플릿을 찾을 수 없습니다."));
    }

    private void assertVersion(long requestedVersion, long currentVersion) {
        if (requestedVersion != currentVersion) {
            throw new OptimisticLockConflictException(currentVersion);
        }
    }

    private StrategyTemplateView toView(StrategyTemplateEntity entity) {
        return new StrategyTemplateView(
                entity.getId().toString(),
                entity.getName(),
                entity.getDescription(),
                entity.getDefaultCycleMinutes(),
                entity.getDefaultPromptText(),
                entity.getDefaultExecutionConfigJson(),
                entity.getDefaultTradingModelProfile().getId().toString(),
                entity.getVersion(),
                entity.getUpdatedAt().toString());
    }

    public sealed interface StrategyTemplateMutation permits CreateStrategyTemplateRequest, UpdateStrategyTemplateRequest {
        String name();

        String description();

        int defaultCycleMinutes();

        String defaultPromptText();

        JsonNode defaultExecutionConfig();

        UUID defaultTradingModelProfileId();
    }

    public record StrategyTemplateView(
            String id,
            String name,
            String description,
            int defaultCycleMinutes,
            String defaultPromptText,
            JsonNode defaultExecutionConfig,
            String defaultTradingModelProfileId,
            long version,
            String updatedAt) {
    }

    public record CreateStrategyTemplateRequest(
            @NotBlank(message = "name은 필수입니다.") @Size(max = 120, message = "name은 120자 이하여야 합니다.") String name,
            @NotBlank(message = "description은 필수입니다.") String description,
            @Min(value = 1, message = "defaultCycleMinutes는 1 이상이어야 합니다.") @Max(value = 30, message = "defaultCycleMinutes는 30 이하여야 합니다.") int defaultCycleMinutes,
            @NotBlank(message = "defaultPromptText는 필수입니다.") String defaultPromptText,
            @NotNull(message = "defaultExecutionConfig는 필수입니다.") JsonNode defaultExecutionConfig,
            @NotNull(message = "defaultTradingModelProfileId는 필수입니다.") UUID defaultTradingModelProfileId)
            implements StrategyTemplateMutation {
    }

    public record UpdateStrategyTemplateRequest(
            @NotBlank(message = "name은 필수입니다.") @Size(max = 120, message = "name은 120자 이하여야 합니다.") String name,
            @NotBlank(message = "description은 필수입니다.") String description,
            @Min(value = 1, message = "defaultCycleMinutes는 1 이상이어야 합니다.") @Max(value = 30, message = "defaultCycleMinutes는 30 이하여야 합니다.") int defaultCycleMinutes,
            @NotBlank(message = "defaultPromptText는 필수입니다.") String defaultPromptText,
            @NotNull(message = "defaultExecutionConfig은 필수입니다.") JsonNode defaultExecutionConfig,
            @NotNull(message = "defaultTradingModelProfileId는 필수입니다.") UUID defaultTradingModelProfileId,
            @NotNull(message = "version은 필수입니다.") Long version)
            implements StrategyTemplateMutation {
    }
}
