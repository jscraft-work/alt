package work.jscraft.alt.integrations.naver;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import work.jscraft.alt.news.application.NewsExternalRecords.ExternalArticleBody;
import work.jscraft.alt.news.application.NewsExternalRecords.ExternalNewsItem;
import work.jscraft.alt.news.application.NewsGateway;
import work.jscraft.alt.news.application.NewsGatewayException;
import work.jscraft.alt.news.application.NewsGatewayException.Category;

@Component
public class NaverNewsAdapter implements NewsGateway {

    static final String VENDOR = "naver";

    private static final Logger log = LoggerFactory.getLogger(NaverNewsAdapter.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_DISPLAY = 100;

    private final NaverProperties properties;
    private final RestClient searchClient;
    private final RestClient articleClient;
    private final ObjectMapper objectMapper;
    @SuppressWarnings("unused")
    private final Clock clock;

    @Autowired
    public NaverNewsAdapter(NaverProperties properties, ObjectMapper objectMapper) {
        this(properties,
                buildRestClient(properties.getSearchTimeoutSeconds()),
                buildRestClient(properties.getArticleTimeoutSeconds()),
                objectMapper,
                Clock.system(KST));
    }

    public NaverNewsAdapter(NaverProperties properties,
                            RestClient searchClient,
                            RestClient articleClient,
                            ObjectMapper objectMapper,
                            Clock clock) {
        this.properties = properties;
        this.searchClient = searchClient;
        this.articleClient = articleClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    private static RestClient buildRestClient(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public List<ExternalNewsItem> fetchNews(String symbolCode, OffsetDateTime sinceInclusive) {
        String clientId = properties.getClientId();
        String clientSecret = properties.getClientSecret();
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new NewsGatewayException(Category.AUTH_FAILED, VENDOR, "네이버 API 키 미설정");
        }
        if (symbolCode == null || symbolCode.isBlank()) {
            throw new NewsGatewayException(Category.REQUEST_INVALID, VENDOR, "symbolCode 미지정");
        }
        if (sinceInclusive == null) {
            throw new NewsGatewayException(Category.REQUEST_INVALID, VENDOR, "sinceInclusive 미지정");
        }

        int display = Math.max(1, Math.min(MAX_DISPLAY, properties.getDisplayLimit()));
        String query = symbolCode + " 주식";

        String url = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .path("/v1/search/news.json")
                .queryParam("query", query)
                .queryParam("display", display)
                .queryParam("sort", "date")
                .build()
                .encode()
                .toUriString();

        ResponseEntity<String> response;
        try {
            response = searchClient.get()
                    .uri(url)
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .retrieve()
                    .toEntity(String.class);
        } catch (HttpClientErrorException ex) {
            throw classifyClientError(ex);
        } catch (HttpServerErrorException ex) {
            throw new NewsGatewayException(Category.TRANSIENT, VENDOR,
                    "네이버 5xx 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (RestClientResponseException ex) {
            throw new NewsGatewayException(Category.TRANSIENT, VENDOR,
                    "네이버 비정상 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw classifyIoException(ex);
        } catch (NewsGatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new NewsGatewayException(Category.TRANSIENT, VENDOR,
                    "네이버 요청 실패: " + ex.getClass().getSimpleName(), ex);
        }

        HttpStatusCode statusCode = response.getStatusCode();
        if (!statusCode.is2xxSuccessful()) {
            throw new NewsGatewayException(Category.TRANSIENT, VENDOR,
                    "네이버 HTTP 비정상 응답 status=" + statusCode.value());
        }

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new NewsGatewayException(Category.INVALID_RESPONSE, VENDOR, "네이버 응답 본문이 비어 있다");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new NewsGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "네이버 응답 JSON 파싱 실패", ex);
        }

        JsonNode items = root.get("items");
        if (items == null || items.isNull() || !items.isArray()) {
            throw new NewsGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "네이버 응답에 items 배열이 없다");
        }

        List<ExternalNewsItem> result = new ArrayList<>();
        Iterator<JsonNode> it = items.elements();
        while (it.hasNext()) {
            JsonNode node = it.next();
            ExternalNewsItem item = toItem(node);
            if (item == null) {
                continue;
            }
            if (item.publishedAt().isBefore(sinceInclusive)) {
                // Naver returns newest first; once we cross the cutoff we can stop.
                break;
            }
            result.add(item);
        }
        log.info("naver news fetched vendor={} status={} query={} count={}",
                VENDOR, statusCode.value(), query, result.size());
        return List.copyOf(result);
    }

    private ExternalNewsItem toItem(JsonNode node) {
        String rawTitle = textOrNull(node.get("title"));
        String rawDescription = textOrNull(node.get("description"));
        String link = textOrNull(node.get("link"));
        String originalLink = textOrNull(node.get("originallink"));
        String pubDate = textOrNull(node.get("pubDate"));

        if (link == null || link.isBlank()) {
            link = originalLink;
        }
        if (link == null || link.isBlank()) {
            throw new NewsGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "네이버 응답 item 에 link/originallink 가 없다");
        }
        if (pubDate == null || pubDate.isBlank()) {
            throw new NewsGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "네이버 응답 item 에 pubDate 가 없다");
        }

        OffsetDateTime publishedAt;
        try {
            publishedAt = OffsetDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new NewsGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "네이버 pubDate 파싱 실패 value=" + pubDate, ex);
        }

        String cleanedTitle = cleanText(rawTitle);
        String cleanedSummary = cleanText(rawDescription);
        String articleUrl = link;
        String externalNewsId = link;

        return new ExternalNewsItem(
                VENDOR,
                externalNewsId,
                cleanedTitle,
                articleUrl,
                publishedAt,
                cleanedSummary);
    }

    @Override
    public Optional<ExternalArticleBody> fetchArticleBody(String articleUrl) {
        if (articleUrl == null || articleUrl.isBlank()) {
            throw new NewsGatewayException(Category.REQUEST_INVALID, VENDOR, "articleUrl 미지정");
        }

        ResponseEntity<String> response;
        try {
            response = articleClient.get()
                    .uri(articleUrl)
                    .header("User-Agent", properties.getUserAgent())
                    .retrieve()
                    .toEntity(String.class);
        } catch (HttpClientErrorException ex) {
            throw new NewsGatewayException(Category.TRANSIENT, VENDOR,
                    "네이버 기사 본문 4xx 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (HttpServerErrorException ex) {
            throw new NewsGatewayException(Category.TRANSIENT, VENDOR,
                    "네이버 기사 본문 5xx 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (RestClientResponseException ex) {
            throw new NewsGatewayException(Category.TRANSIENT, VENDOR,
                    "네이버 기사 본문 비정상 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw classifyIoException(ex);
        } catch (NewsGatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new NewsGatewayException(Category.TRANSIENT, VENDOR,
                    "네이버 기사 본문 요청 실패: " + ex.getClass().getSimpleName(), ex);
        }

        HttpStatusCode statusCode = response.getStatusCode();
        if (!statusCode.is2xxSuccessful()) {
            throw new NewsGatewayException(Category.TRANSIENT, VENDOR,
                    "네이버 기사 본문 HTTP 비정상 응답 status=" + statusCode.value());
        }

        String html = response.getBody();
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        String text = ArticleHtmlStripper.extract(html);
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ExternalArticleBody(text));
    }

    private static NewsGatewayException classifyClientError(HttpClientErrorException ex) {
        int status = ex.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new NewsGatewayException(Category.AUTH_FAILED, VENDOR,
                    "네이버 인증 실패 status=" + status, ex);
        }
        if (status == 429) {
            return new NewsGatewayException(Category.TRANSIENT, VENDOR,
                    "네이버 요청 제한 status=" + status, ex);
        }
        if (status == 400 || status == 404 || status == 422) {
            return new NewsGatewayException(Category.REQUEST_INVALID, VENDOR,
                    "네이버 요청 오류 status=" + status, ex);
        }
        return new NewsGatewayException(Category.TRANSIENT, VENDOR,
                "네이버 4xx 응답 status=" + status, ex);
    }

    private static NewsGatewayException classifyIoException(ResourceAccessException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return new NewsGatewayException(Category.TIMEOUT, VENDOR,
                        "네이버 요청 타임아웃", ex);
            }
            cause = cause.getCause();
        }
        return new NewsGatewayException(Category.TRANSIENT, VENDOR,
                "네이버 네트워크 오류", ex);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        String stripped = stripHtmlTags(value);
        String unescaped = HtmlUtils.htmlUnescape(stripped);
        return collapseWhitespace(unescaped);
    }

    private static String stripHtmlTags(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        boolean inTag = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '<') {
                inTag = true;
                continue;
            }
            if (c == '>') {
                inTag = false;
                continue;
            }
            if (!inTag) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String collapseWhitespace(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        boolean lastSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || c == ' ') {
                if (!lastSpace && sb.length() > 0) {
                    sb.append(' ');
                    lastSpace = true;
                }
            } else {
                sb.append(c);
                lastSpace = false;
            }
        }
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == ' ') {
            end--;
        }
        return sb.substring(0, end);
    }
}
