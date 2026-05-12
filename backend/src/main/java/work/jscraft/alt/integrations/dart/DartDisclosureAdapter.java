package work.jscraft.alt.integrations.dart;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import work.jscraft.alt.disclosure.application.DisclosureExternalRecords.ExternalDisclosureItem;
import work.jscraft.alt.disclosure.application.DisclosureGateway;
import work.jscraft.alt.disclosure.application.DisclosureGatewayException;
import work.jscraft.alt.disclosure.application.DisclosureGatewayException.Category;

@Component
public class DartDisclosureAdapter implements DisclosureGateway {

    static final String VENDOR = "dart";

    private static final Logger log = LoggerFactory.getLogger(DartDisclosureAdapter.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DOCUMENT_URL_PREFIX = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";
    private static final int PAGE_COUNT = 100;
    private static final int PAGE_NO = 1;

    private final DartProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public DartDisclosureAdapter(DartProperties properties, ObjectMapper objectMapper) {
        this(properties, buildRestClient(properties), objectMapper, Clock.system(KST));
    }

    public DartDisclosureAdapter(DartProperties properties,
                                 RestClient restClient,
                                 ObjectMapper objectMapper,
                                 Clock clock) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    private static RestClient buildRestClient(DartProperties properties) {
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        ClientHttpRequestFactory requestFactory = factory;
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<ExternalDisclosureItem> fetchDisclosures(String dartCorpCode, OffsetDateTime sinceInclusive) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new DisclosureGatewayException(Category.AUTH_FAILED, VENDOR, "DART_API_KEY 미설정");
        }
        if (dartCorpCode == null || dartCorpCode.isBlank()) {
            throw new DisclosureGatewayException(Category.REQUEST_INVALID, VENDOR, "corp_code 미지정");
        }
        if (sinceInclusive == null) {
            throw new DisclosureGatewayException(Category.REQUEST_INVALID, VENDOR, "sinceInclusive 미지정");
        }

        LocalDate begin = sinceInclusive.atZoneSameInstant(KST).toLocalDate();
        LocalDate end = LocalDate.now(clock.withZone(KST));
        if (begin.isAfter(end)) {
            return List.of();
        }

        String url = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .path("/api/list.json")
                .queryParam("crtfc_key", apiKey)
                .queryParam("corp_code", dartCorpCode)
                .queryParam("bgn_de", begin.format(DATE_FORMAT))
                .queryParam("end_de", end.format(DATE_FORMAT))
                .queryParam("page_count", PAGE_COUNT)
                .queryParam("page_no", PAGE_NO)
                .build()
                .toUriString();

        ResponseEntity<String> response;
        try {
            response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .toEntity(String.class);
        } catch (ResourceAccessException ex) {
            throw classifyIoException(ex);
        } catch (DisclosureGatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new DisclosureGatewayException(Category.TRANSIENT, VENDOR,
                    "DART 요청 실패: " + ex.getClass().getSimpleName(), ex);
        }

        HttpStatusCode statusCode = response.getStatusCode();
        if (!statusCode.is2xxSuccessful()) {
            throw new DisclosureGatewayException(Category.TRANSIENT, VENDOR,
                    "DART HTTP 비정상 응답 status=" + statusCode.value());
        }

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new DisclosureGatewayException(Category.INVALID_RESPONSE, VENDOR, "DART 응답 본문이 비어 있다");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new DisclosureGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "DART 응답 JSON 파싱 실패", ex);
        }

        JsonNode statusNode = root.get("status");
        if (statusNode == null || !statusNode.isTextual()) {
            throw new DisclosureGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "DART 응답에 status 필드가 없다");
        }
        String status = statusNode.asText();
        switch (status) {
            case "000":
                break;
            case "013":
                return List.of();
            case "010":
            case "011":
            case "012":
                throw new DisclosureGatewayException(Category.AUTH_FAILED, VENDOR,
                        "DART 인증 실패 status=" + status + " message=" + textOrEmpty(root.get("message")));
            case "100":
                throw new DisclosureGatewayException(Category.REQUEST_INVALID, VENDOR,
                        "DART 요청 파라미터 오류 status=" + status + " message=" + textOrEmpty(root.get("message")));
            case "020":
            case "800":
            case "900":
            case "901":
                throw new DisclosureGatewayException(Category.TRANSIENT, VENDOR,
                        "DART 일시적 오류 status=" + status + " message=" + textOrEmpty(root.get("message")));
            default:
                throw new DisclosureGatewayException(Category.INVALID_RESPONSE, VENDOR,
                        "DART 알 수 없는 status=" + status);
        }

        int totalPage = optionalInt(root.get("total_page"));
        if (totalPage > 1) {
            log.warn("dart pagination skipped total_page={} corp_code={}", totalPage, dartCorpCode);
        }

        JsonNode list = root.get("list");
        if (list == null || list.isNull()) {
            return List.of();
        }
        if (!list.isArray()) {
            throw new DisclosureGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "DART 응답 list 필드가 배열이 아니다");
        }

        List<ExternalDisclosureItem> items = new ArrayList<>();
        Iterator<JsonNode> it = list.elements();
        while (it.hasNext()) {
            JsonNode node = it.next();
            items.add(toItem(node, dartCorpCode));
        }
        return List.copyOf(items);
    }

    private ExternalDisclosureItem toItem(JsonNode node, String dartCorpCode) {
        String disclosureNo = requireText(node.get("rcept_no"), "rcept_no");
        String title = requireText(node.get("report_nm"), "report_nm");
        String rceptDt = requireText(node.get("rcept_dt"), "rcept_dt");
        OffsetDateTime publishedAt = parsePublishedAt(rceptDt);
        JsonNode rmNode = node.get("rm");
        String previewText = (rmNode == null || rmNode.isNull()) ? null : rmNode.asText();
        if (previewText != null && previewText.isBlank()) {
            previewText = null;
        }
        String documentUrl = DOCUMENT_URL_PREFIX + disclosureNo;
        return new ExternalDisclosureItem(
                dartCorpCode,
                disclosureNo,
                title,
                publishedAt,
                previewText,
                documentUrl);
    }

    private static OffsetDateTime parsePublishedAt(String rceptDt) {
        try {
            LocalDate date = LocalDate.parse(rceptDt, DATE_FORMAT);
            return date.atStartOfDay(KST).toOffsetDateTime().withOffsetSameInstant(KST_OFFSET);
        } catch (DateTimeParseException ex) {
            throw new DisclosureGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "DART rcept_dt 파싱 실패 value=" + rceptDt, ex);
        }
    }

    private static String requireText(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            throw new DisclosureGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "DART 응답 항목에 " + field + " 필드가 없다");
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            throw new DisclosureGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "DART 응답 항목 " + field + " 값이 비어 있다");
        }
        return value;
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText();
    }

    private static int optionalInt(JsonNode node) {
        if (node == null || node.isNull() || !node.canConvertToInt()) {
            return 0;
        }
        return node.asInt();
    }

    private static DisclosureGatewayException classifyIoException(ResourceAccessException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return new DisclosureGatewayException(Category.TIMEOUT, VENDOR,
                        "DART 요청 타임아웃", ex);
            }
            cause = cause.getCause();
        }
        return new DisclosureGatewayException(Category.TRANSIENT, VENDOR,
                "DART 네트워크 오류", ex);
    }
}
