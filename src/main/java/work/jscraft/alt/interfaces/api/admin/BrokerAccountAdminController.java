package work.jscraft.alt.interfaces.api.admin;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.strategy.application.BrokerAccountService;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.common.dto.ApiSuccessResponse;

@Validated
@RestController
@RequestMapping("/api/admin/broker-accounts")
public class BrokerAccountAdminController {

    private final BrokerAccountService brokerAccountService;

    public BrokerAccountAdminController(BrokerAccountService brokerAccountService) {
        this.brokerAccountService = brokerAccountService;
    }

    @GetMapping
    public ApiDataResponse<List<BrokerAccountService.BrokerAccountView>> list() {
        return new ApiDataResponse<>(brokerAccountService.listBrokerAccounts());
    }

    @PostMapping
    public ApiDataResponse<BrokerAccountService.BrokerAccountView> create(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @Valid @RequestBody BrokerAccountService.CreateBrokerAccountRequest request) {
        return new ApiDataResponse<>(brokerAccountService.createBrokerAccount(request, principal));
    }

    @PatchMapping("/{brokerAccountId}")
    public ApiDataResponse<BrokerAccountService.BrokerAccountView> update(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID brokerAccountId,
            @Valid @RequestBody BrokerAccountService.UpdateBrokerAccountRequest request) {
        return new ApiDataResponse<>(brokerAccountService.updateBrokerAccount(brokerAccountId, request, principal));
    }

    @DeleteMapping("/{brokerAccountId}")
    public ApiDataResponse<ApiSuccessResponse> delete(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID brokerAccountId) {
        brokerAccountService.deleteBrokerAccount(brokerAccountId, principal);
        return new ApiDataResponse<>(new ApiSuccessResponse(true));
    }
}
