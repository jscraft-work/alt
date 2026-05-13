package work.jscraft.alt.strategy.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.DecimalMin;
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
import work.jscraft.alt.common.error.ApiConflictException;
import work.jscraft.alt.common.error.OptimisticLockConflictException;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.ops.application.AuditLogService;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyTemplateRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.trading.application.inputspec.PromptInputSpec;
import work.jscraft.alt.trading.application.inputspec.PromptInputSpecException;
import work.jscraft.alt.trading.application.inputspec.PromptInputSpecParser;

@Service
@Transactional(readOnly = true)
public class StrategyInstanceService {

    public static final String EXECUTION_MODE_PATTERN = "paper|live";
    public static final String LIFECYCLE_STATE_PATTERN = "draft|active|inactive";

    private static final Sort UPDATED_AT_DESC = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final String TARGET_TYPE_STRATEGY_INSTANCE = "STRATEGY_INSTANCE";
    private static final String ACTION_CREATED = "STRATEGY_INSTANCE_CREATED";
    private static final String ACTION_UPDATED = "STRATEGY_INSTANCE_UPDATED";
    private static final String ACTION_LIFECYCLE_CHANGED = "STRATEGY_INSTANCE_LIFECYCLE_CHANGED";
    private static final String ACTION_PROMPT_VERSION_CREATED = "STRATEGY_INSTANCE_PROMPT_VERSION_CREATED";
    private static final String ACTION_PROMPT_VERSION_RESTORED = "STRATEGY_INSTANCE_PROMPT_VERSION_RESTORED";
    private static final String ACTION_WATCHLIST_ASSET_ADDED = "STRATEGY_INSTANCE_WATCHLIST_ASSET_ADDED";
    private static final String ACTION_WATCHLIST_ASSET_REMOVED = "STRATEGY_INSTANCE_WATCHLIST_ASSET_REMOVED";
    private static final String EXECUTION_MODE_LIVE = "live";
    private static final String LIFECYCLE_ACTIVE = "active";
    private static final String LIFECYCLE_DRAFT = "draft";
    private static final String LIFECYCLE_INACTIVE = "inactive";
    private static final String PURPOSE_TRADING_DECISION = "trading_decision";

    private final StrategyInstanceRepository strategyInstanceRepository;
    private final StrategyTemplateRepository strategyTemplateRepository;
    private final StrategyInstancePromptVersionRepository promptVersionRepository;
    private final StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;
    private final BrokerAccountRepository brokerAccountRepository;
    private final LlmModelProfileRepository llmModelProfileRepository;
    private final AssetMasterRepository assetMasterRepository;
    private final AuditLogService auditLogService;
    private final PromptInputSpecParser promptInputSpecParser;

    public StrategyInstanceService(
            StrategyInstanceRepository strategyInstanceRepository,
            StrategyTemplateRepository strategyTemplateRepository,
            StrategyInstancePromptVersionRepository promptVersionRepository,
            StrategyInstanceWatchlistRelationRepository watchlistRelationRepository,
            BrokerAccountRepository brokerAccountRepository,
            LlmModelProfileRepository llmModelProfileRepository,
            AssetMasterRepository assetMasterRepository,
            AuditLogService auditLogService,
            PromptInputSpecParser promptInputSpecParser) {
        this.strategyInstanceRepository = strategyInstanceRepository;
        this.strategyTemplateRepository = strategyTemplateRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.watchlistRelationRepository = watchlistRelationRepository;
        this.brokerAccountRepository = brokerAccountRepository;
        this.llmModelProfileRepository = llmModelProfileRepository;
        this.assetMasterRepository = assetMasterRepository;
        this.auditLogService = auditLogService;
        this.promptInputSpecParser = promptInputSpecParser;
    }

    public List<StrategyInstanceView> listStrategyInstances(String lifecycleState, String executionMode) {
        return strategyInstanceRepository.findAll(UPDATED_AT_DESC).stream()
                .filter(entity -> lifecycleState == null || lifecycleState.equals(entity.getLifecycleState()))
                .filter(entity -> executionMode == null || executionMode.equals(entity.getExecutionMode()))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public StrategyInstanceView createStrategyInstance(CreateStrategyInstanceRequest request, AdminSessionPrincipal actor) {
        StrategyTemplateEntity template = findStrategyTemplate(request.strategyTemplateId());
        BrokerAccountEntity brokerAccount = findBrokerAccount(request.brokerAccountId());
        LlmModelProfileEntity tradingModelProfile = findTradingModelProfileOverride(request.tradingModelProfileId());

        StrategyInstanceEntity entity = new StrategyInstanceEntity();
        entity.setStrategyTemplate(template);
        entity.setName(request.name());
        entity.setLifecycleState(LIFECYCLE_DRAFT);
        entity.setExecutionMode(request.executionMode());
        entity.setBrokerAccount(brokerAccount);
        entity.setBudgetAmount(request.budgetAmount());
        entity.setTradingModelProfile(tradingModelProfile);
        entity.setInputSpecOverrideJson(copyJsonValue(request.inputSpecOverride()));
        entity.setExecutionConfigOverrideJson(copyJsonValue(request.executionConfigOverride()));

        strategyInstanceRepository.saveAndFlush(entity);

        StrategyInstancePromptVersionEntity promptVersion = new StrategyInstancePromptVersionEntity();
        promptVersion.setStrategyInstance(entity);
        promptVersion.setVersionNo(1);
        promptVersion.setPromptText(template.getDefaultPromptText());
        promptVersion.setChangeNote("Initial copy from template");
        promptVersion.setCreatedBy(actor.userId());
        promptVersion = promptVersionRepository.saveAndFlush(promptVersion);

        entity.setCurrentPromptVersion(promptVersion);
        StrategyInstanceView created = toView(strategyInstanceRepository.saveAndFlush(entity));
        auditLogService.recordCreate(
                actor,
                TARGET_TYPE_STRATEGY_INSTANCE,
                entity.getId(),
                ACTION_CREATED,
                created,
                Map.of(
                        "name", created.name(),
                        "lifecycleState", created.lifecycleState(),
                        "executionMode", created.executionMode()));
        return created;
    }

    @Transactional
    public StrategyInstanceView updateStrategyInstance(
            UUID strategyInstanceId,
            UpdateStrategyInstanceRequest request,
            AdminSessionPrincipal actor) {
        StrategyInstanceEntity entity = findStrategyInstance(strategyInstanceId);
        assertVersion(request.version(), entity.getVersion());

        String requestedName = request.hasName()
                ? validateName(request.name())
                : entity.getName();
        String requestedExecutionMode = request.hasExecutionMode()
                ? validateExecutionMode(request.executionMode())
                : entity.getExecutionMode();
        BrokerAccountEntity requestedBrokerAccount = request.hasBrokerAccountId()
                ? findBrokerAccount(request.brokerAccountId())
                : entity.getBrokerAccount();
        BigDecimal requestedBudgetAmount = request.hasBudgetAmount()
                ? validateBudgetAmount(request.budgetAmount())
                : entity.getBudgetAmount();
        LlmModelProfileEntity requestedTradingModelProfile = request.hasTradingModelProfileId()
                ? findTradingModelProfileOverride(request.tradingModelProfileId())
                : entity.getTradingModelProfile();
        JsonNode requestedInputSpec = request.hasInputSpecOverride()
                ? copyJsonValue(request.inputSpecOverride())
                : copyJsonValue(entity.getInputSpecOverrideJson());
        JsonNode requestedExecutionConfig = request.hasExecutionConfigOverride()
                ? copyJsonValue(request.executionConfigOverride())
                : copyJsonValue(entity.getExecutionConfigOverrideJson());

        if (LIFECYCLE_ACTIVE.equals(entity.getLifecycleState())) {
            rejectIfChanged("active 상태에서는 이름을 변경할 수 없습니다.", entity.getName(), requestedName);
            rejectIfChanged("active 상태에서는 실행 모드를 변경할 수 없습니다.", entity.getExecutionMode(), requestedExecutionMode);
            rejectIfChanged(
                    "active 상태에서는 연결 계좌를 변경할 수 없습니다.",
                    brokerAccountId(entity.getBrokerAccount()),
                    brokerAccountId(requestedBrokerAccount));
            rejectIfChanged("active 상태에서는 전략 예산을 변경할 수 없습니다.", entity.getBudgetAmount(), requestedBudgetAmount);
        }
        if (LIFECYCLE_INACTIVE.equals(entity.getLifecycleState())) {
            rejectIfChanged("inactive 상태에서는 전략 예산을 변경할 수 없습니다.", entity.getBudgetAmount(), requestedBudgetAmount);
        }

        StrategyInstanceView before = toView(entity);

        entity.setName(requestedName);
        entity.setExecutionMode(requestedExecutionMode);
        entity.setBrokerAccount(requestedBrokerAccount);
        entity.setBudgetAmount(requestedBudgetAmount);
        entity.setTradingModelProfile(requestedTradingModelProfile);
        entity.setInputSpecOverrideJson(requestedInputSpec);
        entity.setExecutionConfigOverrideJson(requestedExecutionConfig);

        StrategyInstanceView updated = toView(strategyInstanceRepository.saveAndFlush(entity));
        auditLogService.recordUpdate(
                actor,
                TARGET_TYPE_STRATEGY_INSTANCE,
                entity.getId(),
                ACTION_UPDATED,
                before,
                updated,
                Map.of(
                        "name", updated.name(),
                        "lifecycleState", updated.lifecycleState(),
                        "executionMode", updated.executionMode()));
        return updated;
    }

    @Transactional
    public StrategyInstanceView changeLifecycle(
            UUID strategyInstanceId,
            LifecycleChangeRequest request,
            AdminSessionPrincipal actor) {
        StrategyInstanceEntity entity = findStrategyInstance(strategyInstanceId);
        assertVersion(request.version(), entity.getVersion());

        if (request.targetState().equals(entity.getLifecycleState())) {
            return toView(entity);
        }

        StrategyInstanceView before = toView(entity);

        if (LIFECYCLE_ACTIVE.equals(request.targetState())) {
            validateActivatable(entity);
        } else {
            entity.setAutoPausedReason(null);
            entity.setAutoPausedAt(null);
        }

        entity.setLifecycleState(request.targetState());
        StrategyInstanceView updated = toView(strategyInstanceRepository.saveAndFlush(entity));
        auditLogService.recordUpdate(
                actor,
                TARGET_TYPE_STRATEGY_INSTANCE,
                entity.getId(),
                ACTION_LIFECYCLE_CHANGED,
                before,
                updated,
                Map.of(
                        "name", updated.name(),
                        "fromState", before.lifecycleState(),
                        "toState", updated.lifecycleState()));
        return updated;
    }

    public List<PromptVersionView> listPromptVersions(UUID strategyInstanceId) {
        StrategyInstanceEntity entity = findStrategyInstance(strategyInstanceId);
        String currentPromptVersionId = entity.getCurrentPromptVersion() == null
                ? null
                : entity.getCurrentPromptVersion().getId().toString();
        return promptVersionRepository.findByStrategyInstanceIdOrderByVersionNoAsc(strategyInstanceId).stream()
                .map(promptVersion -> toPromptVersionView(promptVersion, currentPromptVersionId))
                .toList();
    }

    @Transactional
    public PromptVersionView createPromptVersion(
            UUID strategyInstanceId,
            CreatePromptVersionRequest request,
            AdminSessionPrincipal actor) {
        StrategyInstanceEntity entity = findStrategyInstanceForUpdate(strategyInstanceId);
        PromptVersionView before = currentPromptVersionView(entity);
        StrategyInstancePromptVersionEntity created = createPromptVersionEntity(
                entity,
                request.promptText(),
                request.changeNote(),
                actor.userId());
        entity.setCurrentPromptVersion(created);
        strategyInstanceRepository.saveAndFlush(entity);

        PromptVersionView createdView = toPromptVersionView(created, created.getId().toString());
        auditLogService.recordUpdate(
                actor,
                TARGET_TYPE_STRATEGY_INSTANCE,
                entity.getId(),
                ACTION_PROMPT_VERSION_CREATED,
                before,
                createdView,
                Map.of(
                        "name", entity.getName(),
                        "versionNo", createdView.versionNo(),
                        "promptVersionId", createdView.id()));
        return createdView;
    }

    @Transactional
    public PromptVersionView restorePromptVersion(
            UUID strategyInstanceId,
            UUID promptVersionId,
            ActivatePromptVersionRequest request,
            AdminSessionPrincipal actor) {
        StrategyInstanceEntity entity = findStrategyInstanceForUpdate(strategyInstanceId);
        assertVersion(request.version(), entity.getVersion());

        StrategyInstancePromptVersionEntity sourceVersion = promptVersionRepository
                .findByIdAndStrategyInstanceId(promptVersionId, strategyInstanceId)
                .orElseThrow(() -> notFound("프롬프트 버전을 찾을 수 없습니다."));
        PromptVersionView before = currentPromptVersionView(entity);

        StrategyInstancePromptVersionEntity restored = createPromptVersionEntity(
                entity,
                sourceVersion.getPromptText(),
                "Restored from version " + sourceVersion.getVersionNo(),
                actor.userId());
        entity.setCurrentPromptVersion(restored);
        strategyInstanceRepository.saveAndFlush(entity);

        PromptVersionView restoredView = toPromptVersionView(restored, restored.getId().toString());
        auditLogService.recordUpdate(
                actor,
                TARGET_TYPE_STRATEGY_INSTANCE,
                entity.getId(),
                ACTION_PROMPT_VERSION_RESTORED,
                before,
                restoredView,
                Map.of(
                        "name", entity.getName(),
                        "sourceVersionNo", sourceVersion.getVersionNo(),
                        "restoredVersionNo", restoredView.versionNo(),
                        "promptVersionId", restoredView.id()));
        return restoredView;
    }

    public List<WatchlistAssetView> listWatchlist(UUID strategyInstanceId) {
        findStrategyInstance(strategyInstanceId);
        return watchlistRelationRepository.findByStrategyInstanceIdOrderByCreatedAtAsc(strategyInstanceId).stream()
                .map(this::toWatchlistAssetView)
                .toList();
    }

    @Transactional
    public WatchlistAssetView addWatchlistAsset(
            UUID strategyInstanceId,
            AddWatchlistAssetRequest request,
            AdminSessionPrincipal actor) {
        StrategyInstanceEntity entity = findStrategyInstanceForUpdate(strategyInstanceId);
        AssetMasterEntity assetMaster = findAssetMaster(request.assetMasterId());
        if (watchlistRelationRepository.existsByStrategyInstanceIdAndAssetMasterId(strategyInstanceId, assetMaster.getId())) {
            throw new ApiConflictException("REQUEST_CONFLICT", "이미 감시 종목에 등록된 자산입니다.");
        }

        int beforeCount = watchlistRelationRepository.findByStrategyInstanceIdOrderByCreatedAtAsc(strategyInstanceId).size();

        StrategyInstanceWatchlistRelationEntity relation = new StrategyInstanceWatchlistRelationEntity();
        relation.setStrategyInstance(entity);
        relation.setAssetMaster(assetMaster);
        WatchlistAssetView created = toWatchlistAssetView(watchlistRelationRepository.saveAndFlush(relation));

        auditLogService.recordUpdate(
                actor,
                TARGET_TYPE_STRATEGY_INSTANCE,
                entity.getId(),
                ACTION_WATCHLIST_ASSET_ADDED,
                Map.of("watchlistCount", beforeCount),
                Map.of("watchlistCount", beforeCount + 1, "assetMasterId", created.assetMasterId()),
                Map.of(
                        "name", entity.getName(),
                        "symbolCode", created.symbolCode(),
                        "assetMasterId", created.assetMasterId()));
        return created;
    }

    @Transactional
    public void removeWatchlistAsset(UUID strategyInstanceId, UUID assetMasterId, AdminSessionPrincipal actor) {
        StrategyInstanceEntity entity = findStrategyInstanceForUpdate(strategyInstanceId);
        StrategyInstanceWatchlistRelationEntity relation = watchlistRelationRepository
                .findByStrategyInstanceIdAndAssetMasterId(strategyInstanceId, assetMasterId)
                .orElseThrow(() -> notFound("감시 종목을 찾을 수 없습니다."));
        WatchlistAssetView removed = toWatchlistAssetView(relation);
        int beforeCount = watchlistRelationRepository.findByStrategyInstanceIdOrderByCreatedAtAsc(strategyInstanceId).size();
        watchlistRelationRepository.delete(relation);
        watchlistRelationRepository.flush();

        auditLogService.recordDelete(
                actor,
                TARGET_TYPE_STRATEGY_INSTANCE,
                entity.getId(),
                ACTION_WATCHLIST_ASSET_REMOVED,
                removed,
                Map.of(
                        "name", entity.getName(),
                        "symbolCode", removed.symbolCode(),
                        "assetMasterId", removed.assetMasterId(),
                        "watchlistCountBefore", beforeCount));
    }

    private void validateActivatable(StrategyInstanceEntity entity) {
        String promptText = entity.getCurrentPromptVersion() == null
                ? null
                : entity.getCurrentPromptVersion().getPromptText();
        if (promptText == null || promptText.isBlank()) {
            throw new ApiConflictException("INSTANCE_NOT_ACTIVATABLE", "현재 프롬프트가 없어 활성화할 수 없습니다.");
        }
        PromptInputSpec parsedInputSpec;
        try {
            parsedInputSpec = promptInputSpecParser.parse(promptText);
        } catch (PromptInputSpecException ex) {
            throw new ApiConflictException(
                    "INSTANCE_NOT_ACTIVATABLE",
                    "prompt frontmatter가 유효하지 않습니다: " + ex.getMessage());
        }

        LlmModelProfileEntity modelProfile = effectiveTradingModelProfile(entity);
        if (modelProfile == null || !PURPOSE_TRADING_DECISION.equals(modelProfile.getPurpose())) {
            throw new ApiConflictException("INSTANCE_NOT_ACTIVATABLE", "트레이딩 모델이 없어 활성화할 수 없습니다.");
        }

        if (entity.getStrategyTemplate().getDefaultCycleMinutes() <= 0) {
            throw new ApiConflictException("INSTANCE_NOT_ACTIVATABLE", "실행 주기가 없어 활성화할 수 없습니다.");
        }

        if (entity.getBudgetAmount() == null || entity.getBudgetAmount().signum() <= 0) {
            throw new ApiConflictException("INSTANCE_NOT_ACTIVATABLE", "전략 예산이 없어 활성화할 수 없습니다.");
        }

        if (parsedInputSpec.scope() == PromptInputSpec.Scope.FULL_WATCHLIST
                && !watchlistRelationRepository.existsByStrategyInstanceId(entity.getId())) {
            throw new ApiConflictException("INSTANCE_NOT_ACTIVATABLE", "full_watchlist 범위는 감시 종목이 1개 이상 있어야 활성화할 수 있습니다.");
        }

        if (EXECUTION_MODE_LIVE.equals(entity.getExecutionMode())) {
            if (entity.getBrokerAccount() == null) {
                throw new ApiConflictException("INSTANCE_NOT_ACTIVATABLE", "live 인스턴스는 연결 계좌가 있어야 활성화할 수 있습니다.");
            }
            if (strategyInstanceRepository.existsByBrokerAccountIdAndExecutionModeAndLifecycleStateAndIdNot(
                    entity.getBrokerAccount().getId(),
                    EXECUTION_MODE_LIVE,
                    LIFECYCLE_ACTIVE,
                    entity.getId())) {
                throw new ApiConflictException("BROKER_ACCOUNT_ALREADY_IN_USE", "이미 다른 active live 인스턴스가 사용 중인 계좌입니다.");
            }
        }
    }

    private JsonNode copyJsonValue(JsonNode requestedValue) {
        return requestedValue == null || requestedValue.isNull()
                ? null
                : requestedValue.deepCopy();
    }

    private LlmModelProfileEntity findTradingModelProfileOverride(UUID requestedModelProfileId) {
        if (requestedModelProfileId == null) {
            return null;
        }
        LlmModelProfileEntity modelProfile = llmModelProfileRepository.findById(requestedModelProfileId)
                .orElseThrow(() -> notFound("트레이딩 모델 프로필을 찾을 수 없습니다."));
        if (!PURPOSE_TRADING_DECISION.equals(modelProfile.getPurpose())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "트레이딩 모델은 trading_decision 용도여야 합니다.");
        }
        return modelProfile;
    }

    private LlmModelProfileEntity effectiveTradingModelProfile(StrategyInstanceEntity entity) {
        if (entity.getTradingModelProfile() != null) {
            return entity.getTradingModelProfile();
        }
        StrategyTemplateEntity template = entity.getStrategyTemplate();
        return template == null ? null : template.getDefaultTradingModelProfile();
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name은 필수입니다.");
        }
        if (name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name은 120자 이하여야 합니다.");
        }
        return name;
    }

    private String validateExecutionMode(String executionMode) {
        if (executionMode == null || executionMode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "executionMode는 필수입니다.");
        }
        if (!executionMode.matches(EXECUTION_MODE_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "executionMode가 올바르지 않습니다.");
        }
        return executionMode;
    }

    private BigDecimal validateBudgetAmount(BigDecimal budgetAmount) {
        if (budgetAmount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "budgetAmount는 필수입니다.");
        }
        if (budgetAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "budgetAmount는 0보다 커야 합니다.");
        }
        return budgetAmount;
    }

    private StrategyTemplateEntity findStrategyTemplate(UUID strategyTemplateId) {
        return strategyTemplateRepository.findById(strategyTemplateId)
                .orElseThrow(() -> notFound("전략 템플릿을 찾을 수 없습니다."));
    }

    private StrategyInstanceEntity findStrategyInstance(UUID strategyInstanceId) {
        return strategyInstanceRepository.findById(strategyInstanceId)
                .orElseThrow(() -> notFound("전략 인스턴스를 찾을 수 없습니다."));
    }

    private StrategyInstanceEntity findStrategyInstanceForUpdate(UUID strategyInstanceId) {
        return strategyInstanceRepository.findByIdForUpdate(strategyInstanceId)
                .orElseThrow(() -> notFound("전략 인스턴스를 찾을 수 없습니다."));
    }

    private BrokerAccountEntity findBrokerAccount(UUID brokerAccountId) {
        if (brokerAccountId == null) {
            return null;
        }
        return brokerAccountRepository.findById(brokerAccountId)
                .orElseThrow(() -> notFound("브로커 계좌를 찾을 수 없습니다."));
    }

    private AssetMasterEntity findAssetMaster(UUID assetMasterId) {
        return assetMasterRepository.findById(assetMasterId)
                .orElseThrow(() -> notFound("종목 마스터를 찾을 수 없습니다."));
    }

    private StrategyInstancePromptVersionEntity createPromptVersionEntity(
            StrategyInstanceEntity entity,
            String promptText,
            String changeNote,
            UUID createdBy) {
        int nextVersionNo = promptVersionRepository.findTopByStrategyInstanceIdOrderByVersionNoDesc(entity.getId())
                .map(StrategyInstancePromptVersionEntity::getVersionNo)
                .orElse(0) + 1;

        StrategyInstancePromptVersionEntity promptVersion = new StrategyInstancePromptVersionEntity();
        promptVersion.setStrategyInstance(entity);
        promptVersion.setVersionNo(nextVersionNo);
        promptVersion.setPromptText(promptText);
        promptVersion.setChangeNote(changeNote);
        promptVersion.setCreatedBy(createdBy);
        return promptVersionRepository.saveAndFlush(promptVersion);
    }

    private UUID brokerAccountId(BrokerAccountEntity brokerAccount) {
        return brokerAccount == null ? null : brokerAccount.getId();
    }

    private void rejectIfChanged(String message, Object currentValue, Object requestedValue) {
        if (!Objects.equals(currentValue, requestedValue)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void assertVersion(long requestedVersion, long currentVersion) {
        if (requestedVersion != currentVersion) {
            throw new OptimisticLockConflictException(currentVersion);
        }
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private StrategyInstanceView toView(StrategyInstanceEntity entity) {
        return new StrategyInstanceView(
                entity.getId().toString(),
                entity.getStrategyTemplate().getId().toString(),
                entity.getStrategyTemplate().getName(),
                entity.getName(),
                entity.getLifecycleState(),
                entity.getExecutionMode(),
                entity.getBrokerAccount() == null ? null : entity.getBrokerAccount().getId().toString(),
                entity.getBrokerAccount() == null ? null : entity.getBrokerAccount().getAccountMasked(),
                entity.getBudgetAmount(),
                entity.getCurrentPromptVersion() == null ? null : entity.getCurrentPromptVersion().getId().toString(),
                entity.getTradingModelProfile() == null ? null : entity.getTradingModelProfile().getId().toString(),
                copyJsonValue(entity.getInputSpecOverrideJson()),
                copyJsonValue(entity.getExecutionConfigOverrideJson()),
                entity.getAutoPausedReason(),
                entity.getAutoPausedAt() == null ? null : entity.getAutoPausedAt().toString(),
                entity.getVersion(),
                entity.getUpdatedAt().toString());
    }

    private PromptVersionView currentPromptVersionView(StrategyInstanceEntity entity) {
        StrategyInstancePromptVersionEntity currentPromptVersion = entity.getCurrentPromptVersion();
        if (currentPromptVersion == null) {
            return null;
        }
        return toPromptVersionView(currentPromptVersion, currentPromptVersion.getId().toString());
    }

    private PromptVersionView toPromptVersionView(
            StrategyInstancePromptVersionEntity entity,
            String currentPromptVersionId) {
        return new PromptVersionView(
                entity.getId().toString(),
                entity.getVersionNo(),
                entity.getPromptText(),
                entity.getChangeNote(),
                entity.getCreatedBy() == null ? null : entity.getCreatedBy().toString(),
                Objects.equals(entity.getId().toString(), currentPromptVersionId),
                entity.getCreatedAt().toString());
    }

    private WatchlistAssetView toWatchlistAssetView(StrategyInstanceWatchlistRelationEntity relation) {
        AssetMasterEntity assetMaster = relation.getAssetMaster();
        return new WatchlistAssetView(
                relation.getId().toString(),
                assetMaster.getId().toString(),
                assetMaster.getSymbolCode(),
                assetMaster.getSymbolName(),
                assetMaster.getMarketType(),
                assetMaster.getDartCorpCode(),
                assetMaster.isHidden(),
                relation.getCreatedAt().toString());
    }

    public record StrategyInstanceView(
            String id,
            String strategyTemplateId,
            String strategyTemplateName,
            String name,
            String lifecycleState,
            String executionMode,
            String brokerAccountId,
            String brokerAccountMasked,
            BigDecimal budgetAmount,
            String currentPromptVersionId,
            String tradingModelProfileId,
            JsonNode inputSpecOverride,
            JsonNode executionConfigOverride,
            String autoPausedReason,
            String autoPausedAt,
            long version,
            String updatedAt) {
    }

    public record PromptVersionView(
            String id,
            int versionNo,
            String promptText,
            String changeNote,
            String createdBy,
            boolean current,
            String createdAt) {
    }

    public record WatchlistAssetView(
            String id,
            String assetMasterId,
            String symbolCode,
            String symbolName,
            String marketType,
            String dartCorpCode,
            boolean hidden,
            String createdAt) {
    }

    public record CreateStrategyInstanceRequest(
            @NotNull(message = "strategyTemplateId는 필수입니다.") UUID strategyTemplateId,
            @NotBlank(message = "name은 필수입니다.") @Size(max = 120, message = "name은 120자 이하여야 합니다.") String name,
            @NotBlank(message = "executionMode는 필수입니다.") @Pattern(
                    regexp = EXECUTION_MODE_PATTERN,
                    message = "executionMode가 올바르지 않습니다.") String executionMode,
            UUID brokerAccountId,
            @NotNull(message = "budgetAmount는 필수입니다.") @DecimalMin(
                    value = "0.0001",
                    message = "budgetAmount는 0보다 커야 합니다.") BigDecimal budgetAmount,
            UUID tradingModelProfileId,
            JsonNode inputSpecOverride,
            JsonNode executionConfigOverride) {
    }

    public static final class UpdateStrategyInstanceRequest {
        private boolean namePresent;
        private String name;
        private boolean executionModePresent;
        private String executionMode;
        private boolean brokerAccountIdPresent;
        private UUID brokerAccountId;
        private boolean budgetAmountPresent;
        private BigDecimal budgetAmount;
        private boolean tradingModelProfileIdPresent;
        private UUID tradingModelProfileId;
        private boolean inputSpecOverridePresent;
        private JsonNode inputSpecOverride;
        private boolean executionConfigOverridePresent;
        private JsonNode executionConfigOverride;
        @NotNull(message = "version은 필수입니다.")
        private Long version;

        public boolean hasName() {
            return namePresent;
        }

        public String name() {
            return name;
        }

        @JsonSetter("name")
        public void setName(String name) {
            this.namePresent = true;
            this.name = name;
        }

        public boolean hasExecutionMode() {
            return executionModePresent;
        }

        public String executionMode() {
            return executionMode;
        }

        @JsonSetter("executionMode")
        public void setExecutionMode(String executionMode) {
            this.executionModePresent = true;
            this.executionMode = executionMode;
        }

        public boolean hasBrokerAccountId() {
            return brokerAccountIdPresent;
        }

        public UUID brokerAccountId() {
            return brokerAccountId;
        }

        @JsonSetter("brokerAccountId")
        public void setBrokerAccountId(UUID brokerAccountId) {
            this.brokerAccountIdPresent = true;
            this.brokerAccountId = brokerAccountId;
        }

        public boolean hasBudgetAmount() {
            return budgetAmountPresent;
        }

        public BigDecimal budgetAmount() {
            return budgetAmount;
        }

        @JsonSetter("budgetAmount")
        public void setBudgetAmount(BigDecimal budgetAmount) {
            this.budgetAmountPresent = true;
            this.budgetAmount = budgetAmount;
        }

        public boolean hasTradingModelProfileId() {
            return tradingModelProfileIdPresent;
        }

        public UUID tradingModelProfileId() {
            return tradingModelProfileId;
        }

        @JsonSetter("tradingModelProfileId")
        public void setTradingModelProfileId(UUID tradingModelProfileId) {
            this.tradingModelProfileIdPresent = true;
            this.tradingModelProfileId = tradingModelProfileId;
        }

        public boolean hasInputSpecOverride() {
            return inputSpecOverridePresent;
        }

        public JsonNode inputSpecOverride() {
            return inputSpecOverride;
        }

        @JsonSetter("inputSpecOverride")
        public void setInputSpecOverride(JsonNode inputSpecOverride) {
            this.inputSpecOverridePresent = true;
            this.inputSpecOverride = inputSpecOverride;
        }

        public boolean hasExecutionConfigOverride() {
            return executionConfigOverridePresent;
        }

        public JsonNode executionConfigOverride() {
            return executionConfigOverride;
        }

        @JsonSetter("executionConfigOverride")
        public void setExecutionConfigOverride(JsonNode executionConfigOverride) {
            this.executionConfigOverridePresent = true;
            this.executionConfigOverride = executionConfigOverride;
        }

        public Long version() {
            return version;
        }

        public void setVersion(Long version) {
            this.version = version;
        }
    }

    public record LifecycleChangeRequest(
            @NotBlank(message = "targetState는 필수입니다.") @Pattern(
                    regexp = LIFECYCLE_STATE_PATTERN,
                    message = "targetState가 올바르지 않습니다.") String targetState,
            @NotNull(message = "version은 필수입니다.") Long version) {
    }

    public record CreatePromptVersionRequest(
            @NotBlank(message = "promptText는 필수입니다.") String promptText,
            String changeNote) {
    }

    public record ActivatePromptVersionRequest(
            @NotNull(message = "version은 필수입니다.") Long version) {
    }

    public record AddWatchlistAssetRequest(
            @NotNull(message = "assetMasterId는 필수입니다.") UUID assetMasterId) {
    }
}
