package work.jscraft.alt.integrations.kis.marketdata;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.util.UriComponentsBuilder;

import work.jscraft.alt.integrations.kis.KisAccessTokenService;
import work.jscraft.alt.integrations.kis.KisProperties;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.application.MarketDataException.Category;
import work.jscraft.alt.marketdata.application.MarketDataGateway;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.FundamentalSnapshot;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.MinuteBar;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.OrderBookSnapshot;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.PriceSnapshot;

@Component
public class KisMarketDataAdapter implements MarketDataGateway {

    static final String VENDOR = "kis";

    private static final Logger log = LoggerFactory.getLogger(KisMarketDataAdapter.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter BAR_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BAR_TIME = DateTimeFormatter.ofPattern("HHmmss");

    private static final String PATH_INQUIRE_PRICE =
            "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String PATH_INQUIRE_TIME_ITEMCHARTPRICE =
            "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice";
    private static final String PATH_INQUIRE_ASKING_PRICE =
            "/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn";

    private static final String TR_ID_PRICE = "FHKST01010100";
    private static final String TR_ID_MINUTE_BAR = "FHKST03010200";
    private static final String TR_ID_ORDERBOOK = "FHKST01010200";

    private final KisProperties properties;
    private final KisAccessTokenService tokenService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public KisMarketDataAdapter(KisProperties properties,
                                KisAccessTokenService tokenService,
                                ObjectMapper objectMapper) {
        this(properties, tokenService, buildRestClient(properties), objectMapper, Clock.system(KST));
    }

    public KisMarketDataAdapter(KisProperties properties,
                                KisAccessTokenService tokenService,
                                RestClient restClient,
                                ObjectMapper objectMapper,
                                Clock clock) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    private static RestClient buildRestClient(KisProperties properties) {
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getRestTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public PriceSnapshot fetchPrice(String symbolCode) {
        requireSymbol(symbolCode);
        String url = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .path(PATH_INQUIRE_PRICE)
                .queryParam("fid_cond_mrkt_div_code", "J")
                .queryParam("fid_input_iscd", symbolCode)
                .build()
                .toUriString();

        JsonNode root = getJsonWithTokenRotation(url, TR_ID_PRICE, Map.of(), "현재가 조회");
        JsonNode output = root.get("output");
        if (output == null || output.isNull() || !output.isObject()) {
            throw new MarketDataException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS 현재가 응답 output 필드가 없다");
        }
        BigDecimal lastPrice = parseBigDecimal(output.get("stck_prpr"));
        if (lastPrice == null) {
            throw new MarketDataException(Category.EMPTY_RESPONSE, VENDOR,
                    "KIS 현재가 응답 stck_prpr 값이 비어 있다");
        }
        BigDecimal openPrice = parseBigDecimal(output.get("stck_oprc"));
        BigDecimal highPrice = parseBigDecimal(output.get("stck_hgpr"));
        BigDecimal lowPrice = parseBigDecimal(output.get("stck_lwpr"));
        BigDecimal volume = parseBigDecimal(output.get("acml_vol"));
        OffsetDateTime snapshotAt = nowKst();
        return new PriceSnapshot(symbolCode, snapshotAt, lastPrice, openPrice, highPrice, lowPrice,
                volume, VENDOR);
    }

    @Override
    public FundamentalSnapshot fetchFundamental(String symbolCode) {
        requireSymbol(symbolCode);
        String url = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .path(PATH_INQUIRE_PRICE)
                .queryParam("fid_cond_mrkt_div_code", "J")
                .queryParam("fid_input_iscd", symbolCode)
                .build()
                .toUriString();

        JsonNode root = getJsonWithTokenRotation(url, TR_ID_PRICE, Map.of(), "펀더멘털 조회");
        JsonNode output = root.get("output");
        if (output == null || output.isNull() || !output.isObject()) {
            throw new MarketDataException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS 펀더멘털 응답 output 필드가 없다");
        }
        return new FundamentalSnapshot(symbolCode, nowKst(), output, VENDOR);
    }

    @Override
    public List<MinuteBar> fetchMinuteBars(String symbolCode, LocalDate businessDate) {
        requireSymbol(symbolCode);
        LocalDate today = LocalDate.now(clock.withZone(KST));
        if (businessDate == null || !businessDate.equals(today)) {
            throw new MarketDataException(Category.REQUEST_INVALID, VENDOR,
                    "KIS inquire-time-itemchartprice는 당일 분봉만 지원합니다");
        }

        // KIS는 fid_input_hour_1을 "조회 종료 시각"으로 본다. 16:00 같은 미래 시각으로
        // 호출하면 장중 응답이 비어 분봉이 못 쌓이므로, 현재 KST 시각을 그대로 넣는다.
        String inquiryHour = OffsetDateTime.now(clock).atZoneSameInstant(KST).format(BAR_TIME);
        List<MinuteBar> collected = new ArrayList<>();
        String ctxFk = "";
        String ctxNk = "";
        boolean tokenRotated = false;
        while (true) {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getBaseUrl())
                    .path(PATH_INQUIRE_TIME_ITEMCHARTPRICE)
                    .queryParam("fid_etc_cls_code", "")
                    .queryParam("fid_cond_mrkt_div_code", "J")
                    .queryParam("fid_input_iscd", symbolCode)
                    .queryParam("fid_input_hour_1", inquiryHour)
                    .queryParam("fid_pw_data_incu_yn", "N")
                    .queryParam("CTX_AREA_FK200", ctxFk)
                    .queryParam("CTX_AREA_NK200", ctxNk);
            String url = builder.build().toUriString();
            String trCont = (ctxFk.isEmpty() && ctxNk.isEmpty()) ? "" : "N";

            PageResult page;
            try {
                page = getMinuteBarPage(url, trCont);
            } catch (MarketDataException ex) {
                if (ex.getCategory() == Category.AUTH_FAILED && !tokenRotated) {
                    tokenService.invalidate();
                    tokenRotated = true;
                    continue;
                }
                throw ex;
            }

            JsonNode output2 = page.root.get("output2");
            if (output2 != null && output2.isArray()) {
                Iterator<JsonNode> it = output2.elements();
                while (it.hasNext()) {
                    JsonNode bar = it.next();
                    MinuteBar mapped = toMinuteBar(symbolCode, bar);
                    if (mapped != null) {
                        collected.add(mapped);
                    }
                }
            }

            String responseTrCont = page.trCont == null ? "" : page.trCont.trim();
            boolean morePages = "F".equals(responseTrCont) || "M".equals(responseTrCont);
            if (!morePages) {
                break;
            }
            String nextFk = textOrEmpty(page.root.get("ctx_area_fk200")).trim();
            String nextNk = textOrEmpty(page.root.get("ctx_area_nk200")).trim();
            if (nextFk.isEmpty() && nextNk.isEmpty()) {
                break;
            }
            ctxFk = nextFk;
            ctxNk = nextNk;
        }

        Collections.reverse(collected);
        return List.copyOf(collected);
    }

    private PageResult getMinuteBarPage(String url, String trCont) {
        Map<String, String> extraHeaders = Map.of("tr_cont", trCont);
        String token = tokenService.getAccessToken();
        ResponseEntity<String> response;
        try {
            response = restClient.get()
                    .uri(url)
                    .headers(headers -> {
                        headers.set("authorization", "Bearer " + token);
                        headers.set("appkey", properties.getAppKey());
                        headers.set("appsecret", properties.getAppSecret());
                        headers.set("tr_id", TR_ID_MINUTE_BAR);
                        headers.set("custtype", "P");
                        for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                            headers.set(e.getKey(), e.getValue());
                        }
                    })
                    .retrieve()
                    .toEntity(String.class);
        } catch (HttpClientErrorException ex) {
            throw classifyClient(ex, "분봉 조회");
        } catch (HttpServerErrorException ex) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS 분봉 5xx 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (RestClientResponseException ex) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS 분봉 비정상 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw classifyIoException(ex, "분봉");
        } catch (MarketDataException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS 분봉 요청 실패: " + ex.getClass().getSimpleName(), ex);
        }
        HttpStatusCode statusCode = response.getStatusCode();
        if (!statusCode.is2xxSuccessful()) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS 분봉 HTTP 비정상 응답 status=" + statusCode.value());
        }
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new MarketDataException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS 분봉 응답 본문이 비어 있다");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new MarketDataException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS 분봉 응답 JSON 파싱 실패", ex);
        }
        Category errorCategory = classifyKisError(root);
        if (errorCategory != null) {
            String message = "KIS 분봉 오류 msg_cd=" + textOrEmpty(root.get("msg_cd"))
                    + " msg1=" + textOrEmpty(root.get("msg1"));
            throw new MarketDataException(errorCategory, VENDOR, message);
        }
        List<String> trContHeader = response.getHeaders().get("tr_cont");
        String trContResp = (trContHeader == null || trContHeader.isEmpty()) ? "" : trContHeader.get(0);
        return new PageResult(root, trContResp);
    }

    private MinuteBar toMinuteBar(String symbolCode, JsonNode bar) {
        String dateStr = textOrEmpty(bar.get("stck_bsop_date")).trim();
        String timeStr = textOrEmpty(bar.get("stck_cntg_hour")).trim();
        if (dateStr.isEmpty() || timeStr.isEmpty()) {
            return null;
        }
        LocalDate date;
        LocalTime time;
        try {
            date = LocalDate.parse(dateStr, BAR_DATE);
            time = LocalTime.parse(timeStr, BAR_TIME);
        } catch (DateTimeParseException ex) {
            throw new MarketDataException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS 분봉 시간 파싱 실패 date=" + dateStr + " time=" + timeStr, ex);
        }
        OffsetDateTime barTime = LocalDateTime.of(date, time).atZone(KST).toOffsetDateTime();
        BigDecimal openPrice = parseBigDecimal(bar.get("stck_oprc"));
        BigDecimal highPrice = parseBigDecimal(bar.get("stck_hgpr"));
        BigDecimal lowPrice = parseBigDecimal(bar.get("stck_lwpr"));
        BigDecimal closePrice = parseBigDecimal(bar.get("stck_prpr"));
        BigDecimal volume = parseBigDecimal(bar.get("cntg_vol"));
        return new MinuteBar(symbolCode, barTime, openPrice, highPrice, lowPrice, closePrice,
                volume, VENDOR);
    }

    @Override
    public OrderBookSnapshot fetchOrderBook(String symbolCode) {
        requireSymbol(symbolCode);
        String url = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .path(PATH_INQUIRE_ASKING_PRICE)
                .queryParam("fid_cond_mrkt_div_code", "J")
                .queryParam("fid_input_iscd", symbolCode)
                .build()
                .toUriString();

        JsonNode root = getJsonWithTokenRotation(url, TR_ID_ORDERBOOK, Map.of(), "호가 조회");
        JsonNode output2 = root.get("output2");
        if (output2 == null || output2.isNull() || !output2.isObject()) {
            throw new MarketDataException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS 호가 응답 output2 필드가 없다");
        }
        OffsetDateTime capturedAt = parseAsprHour(output2.get("aspr_acpt_hour"));
        if (capturedAt == null) {
            capturedAt = nowKst();
        }
        return new OrderBookSnapshot(symbolCode, capturedAt, output2, VENDOR);
    }

    private OffsetDateTime parseAsprHour(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            LocalTime time = LocalTime.parse(raw, BAR_TIME);
            LocalDate today = LocalDate.now(clock.withZone(KST));
            return LocalDateTime.of(today, time).atZone(KST).toOffsetDateTime();
        } catch (DateTimeParseException ex) {
            log.warn("kis aspr_acpt_hour 파싱 실패 raw_len={}", raw.length());
            return null;
        }
    }

    private JsonNode getJsonWithTokenRotation(String url,
                                              String trId,
                                              Map<String, String> extraHeaders,
                                              String label) {
        try {
            return getJson(url, trId, extraHeaders, label);
        } catch (MarketDataException ex) {
            if (ex.getCategory() != Category.AUTH_FAILED) {
                throw ex;
            }
            tokenService.invalidate();
            return getJson(url, trId, extraHeaders, label);
        }
    }

    private JsonNode getJson(String url,
                             String trId,
                             Map<String, String> extraHeaders,
                             String label) {
        String token = tokenService.getAccessToken();
        ResponseEntity<String> response;
        try {
            response = restClient.get()
                    .uri(url)
                    .headers(headers -> {
                        headers.set("authorization", "Bearer " + token);
                        headers.set("appkey", properties.getAppKey());
                        headers.set("appsecret", properties.getAppSecret());
                        headers.set("tr_id", trId);
                        headers.set("custtype", "P");
                        for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                            headers.set(e.getKey(), e.getValue());
                        }
                    })
                    .retrieve()
                    .toEntity(String.class);
        } catch (HttpClientErrorException ex) {
            throw classifyClient(ex, label);
        } catch (HttpServerErrorException ex) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS " + label + " 5xx 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (RestClientResponseException ex) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS " + label + " 비정상 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw classifyIoException(ex, label);
        } catch (MarketDataException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS " + label + " 요청 실패: " + ex.getClass().getSimpleName(), ex);
        }

        HttpStatusCode statusCode = response.getStatusCode();
        if (!statusCode.is2xxSuccessful()) {
            throw new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS " + label + " HTTP 비정상 응답 status=" + statusCode.value());
        }
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new MarketDataException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS " + label + " 응답 본문이 비어 있다");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new MarketDataException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS " + label + " 응답 JSON 파싱 실패", ex);
        }
        Category errorCategory = classifyKisError(root);
        if (errorCategory != null) {
            String message = "KIS " + label + " 오류 msg_cd=" + textOrEmpty(root.get("msg_cd"))
                    + " msg1=" + textOrEmpty(root.get("msg1"));
            throw new MarketDataException(errorCategory, VENDOR, message);
        }
        return root;
    }

    private static Category classifyKisError(JsonNode root) {
        JsonNode rtCdNode = root.get("rt_cd");
        if (rtCdNode == null || rtCdNode.isNull()) {
            return null;
        }
        String rtCd = rtCdNode.asText("").trim();
        if (rtCd.isEmpty() || "0".equals(rtCd)) {
            return null;
        }
        String msgCd = textOrEmpty(root.get("msg_cd")).trim();
        String msg1 = textOrEmpty(root.get("msg1"));
        if ("EGW00121".equals(msgCd) || "EGW00123".equals(msgCd)
                || (msg1 != null && msg1.contains("토큰"))) {
            return Category.AUTH_FAILED;
        }
        return Category.TRANSIENT;
    }

    private static MarketDataException classifyClient(HttpClientErrorException ex, String label) {
        int status = ex.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new MarketDataException(Category.AUTH_FAILED, VENDOR,
                    "KIS " + label + " 인증 실패 status=" + status, ex);
        }
        if (status == 429) {
            return new MarketDataException(Category.TRANSIENT, VENDOR,
                    "KIS " + label + " 요청 제한 status=" + status, ex);
        }
        if (status == 400 || status == 404 || status == 422) {
            return new MarketDataException(Category.REQUEST_INVALID, VENDOR,
                    "KIS " + label + " 요청 오류 status=" + status, ex);
        }
        return new MarketDataException(Category.TRANSIENT, VENDOR,
                "KIS " + label + " 4xx 응답 status=" + status, ex);
    }

    private static MarketDataException classifyIoException(ResourceAccessException ex, String label) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return new MarketDataException(Category.TIMEOUT, VENDOR,
                        "KIS " + label + " 타임아웃", ex);
            }
            cause = cause.getCause();
        }
        return new MarketDataException(Category.TRANSIENT, VENDOR,
                "KIS " + label + " 네트워크 오류", ex);
    }

    private static void requireSymbol(String symbolCode) {
        if (symbolCode == null || symbolCode.isBlank()) {
            throw new MarketDataException(Category.REQUEST_INVALID, VENDOR, "symbolCode 미지정");
        }
    }

    private static BigDecimal parseBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private OffsetDateTime nowKst() {
        return clock.instant().atOffset(KST_OFFSET);
    }

    private record PageResult(JsonNode root, String trCont) {
    }
}
