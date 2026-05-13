package work.jscraft.alt.interfaces.api.admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.auth.security.AdminSessionPrincipal;
import work.jscraft.alt.common.dto.ApiDataResponse;
import work.jscraft.alt.strategy.application.PromptVocabulary;
import work.jscraft.alt.strategy.application.PromptVocabularyProvider;

@Validated
@RestController
@RequestMapping("/api/admin/prompt-vocabulary")
public class PromptVocabularyAdminController {

    private final PromptVocabularyProvider provider;

    public PromptVocabularyAdminController(PromptVocabularyProvider provider) {
        this.provider = provider;
    }

    @GetMapping
    public ApiDataResponse<PromptVocabulary> vocabulary(
            @AuthenticationPrincipal AdminSessionPrincipal principal) {
        return new ApiDataResponse<>(provider.vocabulary());
    }
}
