package work.jscraft.alt.integrations.llm;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.news.application.NewsAssessmentEngine;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmExecutableProperties;
import work.jscraft.alt.trading.application.decision.LlmRequest;

@Component
public class LlmNewsAssessmentAdapter implements NewsAssessmentEngine {

    static final String PURPOSE = "news_assessment";
    static final String BLANK_PLACEHOLDER = "(없음)";
    static final String PROMPT_TEMPLATE = """
            다음 뉴스가 한국 주식 단타 트레이딩 판단에 도움이 되는지 평가해 주세요.

            <제목>
            {title}

            <요약>
            {summary}

            <본문>
            {bodyText}

            응답은 정확히 다음 JSON 형식 한 줄로만 해주세요. 부가 설명 없이.
            {"useful": true}  또는  {"useful": false}
            """;

    private static final Logger log = LoggerFactory.getLogger(LlmNewsAssessmentAdapter.class);

    private final LlmModelProfileRepository repository;
    private final LlmHttpAdapter httpAdapter;
    private final LlmExecutableProperties executableProperties;
    private final ObjectMapper objectMapper;

    public LlmNewsAssessmentAdapter(
            LlmModelProfileRepository repository,
            LlmHttpAdapter httpAdapter,
            LlmExecutableProperties executableProperties,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.httpAdapter = httpAdapter;
        this.executableProperties = executableProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Verdict assess(AssessmentRequest request) {
        List<LlmModelProfileEntity> profiles = repository.findByPurposeAndEnabledTrue(PURPOSE);
        if (profiles == null || profiles.isEmpty()) {
            log.warn("no news_assessment profile enabled; returning UNCLASSIFIED");
            return new Verdict(UsefulnessStatus.UNCLASSIFIED, null);
        }
        LlmModelProfileEntity profile = profiles.get(0);
        if (profiles.size() > 1) {
            log.debug("multiple news_assessment profiles, using first id={}", profile.getId());
        }

        String prompt = buildPrompt(request);

        int configuredSeconds = executableProperties.forProvider(profile.getProvider()).getTimeoutSeconds();
        Duration timeout = Duration.ofSeconds(Math.max(1, configuredSeconds));

        LlmRequest llmRequest = new LlmRequest(
                UUID.randomUUID(),
                profile.getProvider(),
                profile.getModelName(),
                prompt,
                timeout);

        LlmCallResult result = httpAdapter.execute(llmRequest);
        if (!result.isSuccess()) {
            log.warn("news_assessment http call failed: callStatus={} failureMessage={}",
                    result.callStatus(), result.failureMessage());
            return new Verdict(UsefulnessStatus.UNCLASSIFIED, profile.getId());
        }

        UsefulnessStatus status = parseStatus(result.stdoutText());
        return new Verdict(status, profile.getId());
    }

    String buildPrompt(AssessmentRequest request) {
        return PROMPT_TEMPLATE
                .replace("{title}", substitute(request.title()))
                .replace("{summary}", substitute(request.summary()))
                .replace("{bodyText}", substitute(request.bodyText()));
    }

    private String substitute(String value) {
        if (value == null || value.isBlank()) {
            return BLANK_PLACEHOLDER;
        }
        return value;
    }

    private UsefulnessStatus parseStatus(String stdoutText) {
        if (stdoutText == null) {
            return UsefulnessStatus.UNCLASSIFIED;
        }
        String trimmed = stdoutText.trim();
        int braceIdx = trimmed.indexOf('{');
        if (braceIdx < 0) {
            return UsefulnessStatus.UNCLASSIFIED;
        }
        String jsonCandidate = trimmed.substring(braceIdx);
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonCandidate);
        } catch (JsonProcessingException ex) {
            return UsefulnessStatus.UNCLASSIFIED;
        }
        JsonNode usefulNode = root.get("useful");
        if (usefulNode == null || usefulNode.isNull()) {
            return UsefulnessStatus.UNCLASSIFIED;
        }
        if (usefulNode.isBoolean()) {
            return usefulNode.asBoolean() ? UsefulnessStatus.USEFUL : UsefulnessStatus.NOT_USEFUL;
        }
        if (usefulNode.isTextual()) {
            String text = usefulNode.asText().trim();
            if (text.equalsIgnoreCase("true")) {
                return UsefulnessStatus.USEFUL;
            }
            if (text.equalsIgnoreCase("false")) {
                return UsefulnessStatus.NOT_USEFUL;
            }
        }
        return UsefulnessStatus.UNCLASSIFIED;
    }
}
