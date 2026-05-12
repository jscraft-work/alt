package work.jscraft.alt.interfaces.api.admin;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.marketdata.application.AssetMasterService;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.common.dto.ApiSuccessResponse;

@Validated
@RestController
@RequestMapping("/api/admin/assets")
public class AssetMasterAdminController {

    private final AssetMasterService assetMasterService;

    public AssetMasterAdminController(AssetMasterService assetMasterService) {
        this.assetMasterService = assetMasterService;
    }

    @GetMapping
    public ApiDataResponse<List<AssetMasterService.AssetMasterView>> list(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Boolean hidden) {
        return new ApiDataResponse<>(assetMasterService.listAssets(query, hidden));
    }

    @GetMapping("/dart-corp-code-lookup")
    public ApiDataResponse<AssetMasterService.DartCorpCodeLookupView> lookupDartCorpCode(
            @RequestParam @NotBlank(message = "symbolCode는 필수입니다.") String symbolCode) {
        return new ApiDataResponse<>(assetMasterService.lookupDartCorpCode(symbolCode));
    }

    @PostMapping
    public ApiDataResponse<AssetMasterService.AssetMasterView> create(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @Valid @RequestBody AssetMasterService.CreateAssetMasterRequest request) {
        return new ApiDataResponse<>(assetMasterService.createAsset(request, principal));
    }

    @PatchMapping("/{assetMasterId}")
    public ApiDataResponse<AssetMasterService.AssetMasterView> update(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID assetMasterId,
            @Valid @RequestBody AssetMasterService.UpdateAssetMasterRequest request) {
        return new ApiDataResponse<>(assetMasterService.updateAsset(assetMasterId, request, principal));
    }

    @DeleteMapping("/{assetMasterId}")
    public ApiDataResponse<ApiSuccessResponse> delete(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID assetMasterId) {
        assetMasterService.deleteAsset(assetMasterId, principal);
        return new ApiDataResponse<>(new ApiSuccessResponse(true));
    }
}
