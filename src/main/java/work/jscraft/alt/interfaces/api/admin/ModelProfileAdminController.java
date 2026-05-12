package work.jscraft.alt.interfaces.api.admin;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

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

import work.jscraft.alt.llm.application.LlmModelProfileService;
import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.common.dto.ApiSuccessResponse;

@Validated
@RestController
@RequestMapping("/api/admin/model-profiles")
public class ModelProfileAdminController {

    private final LlmModelProfileService llmModelProfileService;

    public ModelProfileAdminController(LlmModelProfileService llmModelProfileService) {
        this.llmModelProfileService = llmModelProfileService;
    }

    @GetMapping
    public ApiDataResponse<List<LlmModelProfileService.ModelProfileView>> list(
            @RequestParam(required = false) @Pattern(
                    regexp = LlmModelProfileService.MODEL_PURPOSE_PATTERN,
                    message = "purpose가 올바르지 않습니다.") String purpose,
            @RequestParam(required = false) Boolean enabled) {
        return new ApiDataResponse<>(llmModelProfileService.listModelProfiles(purpose, enabled));
    }

    @PostMapping
    public ApiDataResponse<LlmModelProfileService.ModelProfileView> create(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @Valid @RequestBody LlmModelProfileService.CreateModelProfileRequest request) {
        return new ApiDataResponse<>(llmModelProfileService.createModelProfile(request, principal));
    }

    @PatchMapping("/{modelProfileId}")
    public ApiDataResponse<LlmModelProfileService.ModelProfileView> update(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID modelProfileId,
            @Valid @RequestBody LlmModelProfileService.UpdateModelProfileRequest request) {
        return new ApiDataResponse<>(llmModelProfileService.updateModelProfile(modelProfileId, request, principal));
    }

    @DeleteMapping("/{modelProfileId}")
    public ApiDataResponse<ApiSuccessResponse> delete(
            @AuthenticationPrincipal AdminSessionPrincipal principal,
            @PathVariable UUID modelProfileId) {
        llmModelProfileService.deleteModelProfile(modelProfileId, principal);
        return new ApiDataResponse<>(new ApiSuccessResponse(true));
    }
}
