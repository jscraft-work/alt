package work.jscraft.alt.strategy.application;

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
import work.jscraft.alt.ops.application.AuditLogService;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountRepository;

@Service
@Transactional(readOnly = true)
public class BrokerAccountService {

    private static final Sort UPDATED_AT_DESC = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final String TARGET_TYPE = "BROKER_ACCOUNT";

    private final BrokerAccountRepository brokerAccountRepository;
    private final AuditLogService auditLogService;

    public BrokerAccountService(BrokerAccountRepository brokerAccountRepository, AuditLogService auditLogService) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.auditLogService = auditLogService;
    }

    public List<BrokerAccountView> listBrokerAccounts() {
        return brokerAccountRepository.findAll(UPDATED_AT_DESC).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public BrokerAccountView createBrokerAccount(CreateBrokerAccountRequest request, AdminSessionPrincipal actor) {
        brokerAccountRepository.findByBrokerCodeAndBrokerAccountNo(request.brokerCode(), request.brokerAccountNo())
                .ifPresent(existing -> {
                    throw new ApiConflictException("REQUEST_CONFLICT", "이미 존재하는 브로커 계좌입니다.");
                });

        BrokerAccountEntity entity = new BrokerAccountEntity();
        apply(entity, request);
        BrokerAccountView created = toView(brokerAccountRepository.saveAndFlush(entity));
        auditLogService.recordCreate(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "BROKER_ACCOUNT_CREATED",
                created,
                Map.of("brokerCode", created.brokerCode(), "accountAlias", safeString(created.accountAlias())));
        return created;
    }

    @Transactional
    public BrokerAccountView updateBrokerAccount(
            UUID brokerAccountId,
            UpdateBrokerAccountRequest request,
            AdminSessionPrincipal actor) {
        BrokerAccountEntity entity = findBrokerAccount(brokerAccountId);
        assertVersion(request.version(), entity.getVersion());

        brokerAccountRepository.findByBrokerCodeAndBrokerAccountNo(request.brokerCode(), request.brokerAccountNo())
                .filter(existing -> !existing.getId().equals(brokerAccountId))
                .ifPresent(existing -> {
                    throw new ApiConflictException("REQUEST_CONFLICT", "이미 존재하는 브로커 계좌입니다.");
                });

        BrokerAccountView before = toView(entity);
        apply(entity, request);
        BrokerAccountView updated = toView(brokerAccountRepository.saveAndFlush(entity));
        auditLogService.recordUpdate(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "BROKER_ACCOUNT_UPDATED",
                before,
                updated,
                Map.of("brokerCode", updated.brokerCode(), "accountAlias", safeString(updated.accountAlias())));
        return updated;
    }

    @Transactional
    public void deleteBrokerAccount(UUID brokerAccountId, AdminSessionPrincipal actor) {
        BrokerAccountEntity entity = findBrokerAccount(brokerAccountId);
        BrokerAccountView before = toView(entity);
        brokerAccountRepository.delete(entity);
        brokerAccountRepository.flush();
        auditLogService.recordDelete(
                actor,
                TARGET_TYPE,
                entity.getId(),
                "BROKER_ACCOUNT_DELETED",
                before,
                Map.of("brokerCode", before.brokerCode(), "accountAlias", safeString(before.accountAlias())));
    }

    private void apply(BrokerAccountEntity entity, BrokerAccountMutation request) {
        entity.setBrokerCode(request.brokerCode());
        entity.setBrokerAccountNo(request.brokerAccountNo());
        entity.setAccountAlias(request.accountAlias());
        entity.setAccountMasked(request.accountMasked());
    }

    private BrokerAccountEntity findBrokerAccount(UUID brokerAccountId) {
        return brokerAccountRepository.findById(brokerAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "브로커 계좌를 찾을 수 없습니다."));
    }

    private void assertVersion(long requestedVersion, long currentVersion) {
        if (requestedVersion != currentVersion) {
            throw new OptimisticLockConflictException(currentVersion);
        }
    }

    private BrokerAccountView toView(BrokerAccountEntity entity) {
        return new BrokerAccountView(
                entity.getId().toString(),
                entity.getBrokerCode(),
                entity.getAccountAlias(),
                entity.getAccountMasked(),
                entity.getVersion(),
                entity.getUpdatedAt().toString());
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    public sealed interface BrokerAccountMutation permits CreateBrokerAccountRequest, UpdateBrokerAccountRequest {
        String brokerCode();

        String brokerAccountNo();

        String accountAlias();

        String accountMasked();
    }

    public record BrokerAccountView(
            String id,
            String brokerCode,
            String accountAlias,
            String accountMasked,
            long version,
            String updatedAt) {
    }

    public record CreateBrokerAccountRequest(
            @NotBlank(message = "brokerCode는 필수입니다.") @Size(max = 40, message = "brokerCode는 40자 이하여야 합니다.") String brokerCode,
            @NotBlank(message = "brokerAccountNo는 필수입니다.") @Size(max = 120, message = "brokerAccountNo는 120자 이하여야 합니다.") String brokerAccountNo,
            @Size(max = 120, message = "accountAlias는 120자 이하여야 합니다.") String accountAlias,
            @NotBlank(message = "accountMasked는 필수입니다.") @Size(max = 120, message = "accountMasked는 120자 이하여야 합니다.") String accountMasked)
            implements BrokerAccountMutation {
    }

    public record UpdateBrokerAccountRequest(
            @NotBlank(message = "brokerCode는 필수입니다.") @Size(max = 40, message = "brokerCode는 40자 이하여야 합니다.") String brokerCode,
            @NotBlank(message = "brokerAccountNo는 필수입니다.") @Size(max = 120, message = "brokerAccountNo는 120자 이하여야 합니다.") String brokerAccountNo,
            @Size(max = 120, message = "accountAlias는 120자 이하여야 합니다.") String accountAlias,
            @NotBlank(message = "accountMasked는 필수입니다.") @Size(max = 120, message = "accountMasked는 120자 이하여야 합니다.") String accountMasked,
            @NotNull(message = "version은 필수입니다.") Long version)
            implements BrokerAccountMutation {
    }
}
