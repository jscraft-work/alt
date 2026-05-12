package work.jscraft.alt.marketdata.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.ops.application.AuditLogService;

@Service
@Transactional(readOnly = true)
public class AssetMasterService {

    private static final Sort UPDATED_AT_DESC = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final String TARGET_TYPE = "ASSET_MASTER";

    private final AssetMasterRepository assetMasterRepository;
    private final AuditLogService auditLogService;

    public AssetMasterService(AssetMasterRepository assetMasterRepository, AuditLogService auditLogService) {
        this.assetMasterRepository = assetMasterRepository;
        this.auditLogService = auditLogService;
    }

    public List<AssetMasterView> listAssets(String query, Boolean hidden) {
        String normalizedQuery = query == null ? null : query.trim().toLowerCase();
        return assetMasterRepository.findAll(UPDATED_AT_DESC).stream()
                .filter(entity -> normalizedQuery == null || normalizedQuery.isEmpty()
                        || entity.getSymbolCode().toLowerCase().contains(normalizedQuery)
                        || entity.getSymbolName().toLowerCase().contains(normalizedQuery))
                .filter(entity -> hidden == null || entity.isHidden() == hidden.booleanValue())
                .map(this::toView)
                .toList();
    }

    public DartCorpCodeLookupView lookupDartCorpCode(String symbolCode) {
        AssetMasterEntity assetMaster = assetMasterRepository.findBySymbolCode(symbolCode.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목 마스터를 찾을 수 없습니다."));
        return new DartCorpCodeLookupView(
                assetMaster.getId().toString(),
                assetMaster.getSymbolCode(),
                assetMaster.getSymbolName(),
                assetMaster.getDartCorpCode(),
                assetMaster.isHidden());
    }

    @Transactional
    public AssetMasterView createAsset(CreateAssetMasterRequest request, AdminSessionPrincipal actor) {
        assetMasterRepository.findBySymbolCode(request.symbolCode()).ifPresent(existing -> {
            throw new ApiConflictException("REQUEST_CONFLICT", "이미 존재하는 종목 코드입니다.");
        });

        AssetMasterEntity entity = new AssetMasterEntity();
        apply(entity, request);
        AssetMasterView created = toView(assetMasterRepository.saveAndFlush(entity));
        auditLogService.recordCreate(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "ASSET_MASTER_CREATED",
                created,
                Map.of("symbolCode", created.symbolCode(), "symbolName", created.symbolName()));
        return created;
    }

    @Transactional
    public AssetMasterView updateAsset(UUID assetMasterId, UpdateAssetMasterRequest request, AdminSessionPrincipal actor) {
        AssetMasterEntity entity = findAsset(assetMasterId);
        assertVersion(request.version(), entity.getVersion());

        assetMasterRepository.findBySymbolCode(request.symbolCode())
                .filter(existing -> !existing.getId().equals(assetMasterId))
                .ifPresent(existing -> {
                    throw new ApiConflictException("REQUEST_CONFLICT", "이미 존재하는 종목 코드입니다.");
                });

        AssetMasterView before = toView(entity);
        apply(entity, request);
        AssetMasterView updated = toView(assetMasterRepository.saveAndFlush(entity));
        auditLogService.recordUpdate(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "ASSET_MASTER_UPDATED",
                before,
                updated,
                Map.of("symbolCode", updated.symbolCode(), "symbolName", updated.symbolName()));
        return updated;
    }

    @Transactional
    public void deleteAsset(UUID assetMasterId, AdminSessionPrincipal actor) {
        AssetMasterEntity entity = findAsset(assetMasterId);
        AssetMasterView before = toView(entity);
        assetMasterRepository.delete(entity);
        assetMasterRepository.flush();
        auditLogService.recordDelete(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "ASSET_MASTER_DELETED",
                before,
                Map.of("symbolCode", before.symbolCode(), "symbolName", before.symbolName()));
    }

    private void apply(AssetMasterEntity entity, AssetMasterMutation request) {
        entity.setSymbolCode(request.symbolCode());
        entity.setSymbolName(request.symbolName());
        entity.setMarketType(request.marketType());
        entity.setDartCorpCode(request.dartCorpCode());
        entity.setHidden(request.hidden());
    }

    private AssetMasterEntity findAsset(UUID assetMasterId) {
        return assetMasterRepository.findById(assetMasterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목 마스터를 찾을 수 없습니다."));
    }

    private void assertVersion(long requestedVersion, long currentVersion) {
        if (requestedVersion != currentVersion) {
            throw new OptimisticLockConflictException(currentVersion);
        }
    }

    private AssetMasterView toView(AssetMasterEntity entity) {
        return new AssetMasterView(
                entity.getId().toString(),
                entity.getSymbolCode(),
                entity.getSymbolName(),
                entity.getMarketType(),
                entity.getDartCorpCode(),
                entity.isHidden(),
                entity.getVersion(),
                entity.getUpdatedAt().toString());
    }

    public sealed interface AssetMasterMutation permits CreateAssetMasterRequest, UpdateAssetMasterRequest {
        String symbolCode();

        String symbolName();

        String marketType();

        String dartCorpCode();

        boolean hidden();
    }

    public record AssetMasterView(
            String id,
            String symbolCode,
            String symbolName,
            String marketType,
            String dartCorpCode,
            boolean hidden,
            long version,
            String updatedAt) {
    }

    public record DartCorpCodeLookupView(
            String assetMasterId,
            String symbolCode,
            String symbolName,
            String dartCorpCode,
            boolean hidden) {
    }

    public record CreateAssetMasterRequest(
            @NotBlank(message = "symbolCode는 필수입니다.") @Size(max = 40, message = "symbolCode는 40자 이하여야 합니다.") String symbolCode,
            @NotBlank(message = "symbolName은 필수입니다.") @Size(max = 200, message = "symbolName은 200자 이하여야 합니다.") String symbolName,
            @NotBlank(message = "marketType는 필수입니다.") @Size(max = 40, message = "marketType는 40자 이하여야 합니다.") String marketType,
            @Size(max = 20, message = "dartCorpCode는 20자 이하여야 합니다.") String dartCorpCode,
            boolean hidden)
            implements AssetMasterMutation {
    }

    public record UpdateAssetMasterRequest(
            @NotBlank(message = "symbolCode는 필수입니다.") @Size(max = 40, message = "symbolCode는 40자 이하여야 합니다.") String symbolCode,
            @NotBlank(message = "symbolName은 필수입니다.") @Size(max = 200, message = "symbolName은 200자 이하여야 합니다.") String symbolName,
            @NotBlank(message = "marketType는 필수입니다.") @Size(max = 40, message = "marketType는 40자 이하여야 합니다.") String marketType,
            @Size(max = 20, message = "dartCorpCode는 20자 이하여야 합니다.") String dartCorpCode,
            boolean hidden,
            @NotNull(message = "version은 필수입니다.") Long version)
            implements AssetMasterMutation {
    }
}
