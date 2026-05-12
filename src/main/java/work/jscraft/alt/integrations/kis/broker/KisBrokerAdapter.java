package work.jscraft.alt.integrations.kis.broker;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
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
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.trading.application.broker.AccountStatus;
import work.jscraft.alt.trading.application.broker.AccountStatus.HoldingItem;
import work.jscraft.alt.trading.application.broker.BrokerGateway;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException.Category;
import work.jscraft.alt.trading.application.broker.BrokerOrderRequest;
import work.jscraft.alt.trading.application.broker.OrderStatusResult;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;

@Component
public class KisBrokerAdapter implements BrokerGateway {

    static final String VENDOR = "kis";

    private static final Logger log = LoggerFactory.getLogger(KisBrokerAdapter.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String DEFAULT_REAL_BASE_URL = "https://openapi.koreainvestment.com:9443";
    private static final String DEFAULT_DEMO_BASE_URL = "https://openapivts.koreainvestment.com:29443";

    private static final String PATH_INQUIRE_BALANCE =
            "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String PATH_ORDER_CASH =
            "/uapi/domestic-stock/v1/trading/order-cash";
    private static final String PATH_INQUIRE_DAILY_CCLD =
            "/uapi/domestic-stock/v1/trading/inquire-daily-ccld";

    private static final String TR_ID_BALANCE_REAL = "TTTC8434R";
    private static final String TR_ID_BALANCE_DEMO = "VTTC8434R";
    private static final String TR_ID_ORDER_BUY_REAL = "TTTC0012U";
    private static final String TR_ID_ORDER_SELL_REAL = "TTTC0011U";
    private static final String TR_ID_ORDER_BUY_DEMO = "VTTC0012U";
    private static final String TR_ID_ORDER_SELL_DEMO = "VTTC0011U";
    private static final String TR_ID_DAILY_CCLD_REAL = "TTTC0081R";
    private static final String TR_ID_DAILY_CCLD_DEMO = "VTTC0081R";

    private final KisProperties properties;
    private final KisAccessTokenService tokenService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final KisResponseMapper responseMapper;
    private final Clock clock;

    @Autowired
    public KisBrokerAdapter(KisProperties properties,
                            KisAccessTokenService tokenService,
                            ObjectMapper objectMapper,
                            KisResponseMapper responseMapper) {
        this(properties, tokenService, buildRestClient(properties), objectMapper, responseMapper,
                Clock.system(KST));
    }

    public KisBrokerAdapter(KisProperties properties,
                            KisAccessTokenService tokenService,
                            RestClient restClient,
                            ObjectMapper objectMapper,
                            KisResponseMapper responseMapper,
                            Clock clock) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.responseMapper = responseMapper;
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

    /**
     * 운영 환경 식별. baseUrl이 명시적으로 데모/테스트 호스트를 가리키더라도
     * tr_id 결정은 {@code environment} 속성을 1차 기준으로 한다.
     */
    private boolean isDemoEnvironment() {
        String env = properties.getEnvironment();
        if (env == null || env.isBlank()) {
            return false;
        }
        return "demo".equalsIgnoreCase(env.trim());
    }

    /**
     * REST baseUrl 해석. 호출자가 baseUrl을 명시적으로 디폴트와 다른 값으로 바꿔둔 경우
     * 테스트 주입/내부망 라우팅 의도로 보고 그대로 사용한다. 그렇지 않으면 environment 매핑.
     */
    private String resolveBaseUrl() {
        String configured = properties.getBaseUrl();
        if (configured != null && !configured.isBlank() && !DEFAULT_REAL_BASE_URL.equals(configured)) {
            return configured;
        }
        return isDemoEnvironment() ? DEFAULT_DEMO_BASE_URL : DEFAULT_REAL_BASE_URL;
    }

    // -----------------------------------------------------------------
    // BrokerGateway 구현
    // -----------------------------------------------------------------

    @Override
    public AccountStatus getAccountStatus(BrokerAccountEntity brokerAccount) {
        AccountIdentifier acct = parseAccount(brokerAccount);
        String trId = isDemoEnvironment() ? TR_ID_BALANCE_DEMO : TR_ID_BALANCE_REAL;

        List<HoldingItem> holdings = new ArrayList<>();
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal totalAssetAmount = BigDecimal.ZERO;

        String ctxFk = "";
        String ctxNk = "";
        boolean tokenRotated = false;

        while (true) {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(resolveBaseUrl())
                    .path(PATH_INQUIRE_BALANCE)
                    .queryParam("CANO", acct.cano())
                    .queryParam("ACNT_PRDT_CD", acct.acntPrdtCd())
                    .queryParam("AFHR_FLPR_YN", "N")
                    .queryParam("OFL_YN", "")
                    .queryParam("INQR_DVSN", "02")
                    .queryParam("UNPR_DVSN", "01")
                    .queryParam("FUND_STTL_ICLD_YN", "N")
                    .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")
                    .queryParam("PRCS_DVSN", "00")
                    .queryParam("CTX_AREA_FK100", ctxFk)
                    .queryParam("CTX_AREA_NK100", ctxNk);
            String url = builder.build().toUriString();
            String trCont = (ctxFk.isEmpty() && ctxNk.isEmpty()) ? "" : "N";

            PageResult page;
            try {
                page = httpGet(url, trId, trCont, "계좌 잔고");
            } catch (BrokerGatewayException ex) {
                if (ex.getCategory() == Category.AUTH_FAILED && !tokenRotated) {
                    tokenService.invalidate();
                    tokenRotated = true;
                    continue;
                }
                throw ex;
            }

            JsonNode root = page.root();
            JsonNode output1 = root.get("output1");
            if (output1 != null && output1.isArray()) {
                for (JsonNode pos : output1) {
                    HoldingItem holding = toHoldingItem(pos);
                    if (holding != null) {
                        holdings.add(holding);
                    }
                }
            }

            JsonNode output2 = root.get("output2");
            if (output2 != null && output2.isArray() && output2.size() > 0) {
                JsonNode summary = output2.get(0);
                BigDecimal cash = KisResponseMapper.parseDecimal(summary.get("dnca_tot_amt"));
                BigDecimal total = KisResponseMapper.parseDecimal(summary.get("tot_evlu_amt"));
                if (cash != null) {
                    cashAmount = cash;
                }
                if (total != null) {
                    totalAssetAmount = total;
                }
            }

            String responseTrCont = page.trCont() == null ? "" : page.trCont().trim();
            boolean morePages = "F".equals(responseTrCont) || "M".equals(responseTrCont);
            if (!morePages) {
                break;
            }
            String nextFk = KisResponseMapper.text(root.get("ctx_area_fk100")).trim();
            String nextNk = KisResponseMapper.text(root.get("ctx_area_nk100")).trim();
            if (nextFk.isEmpty() && nextNk.isEmpty()) {
                break;
            }
            ctxFk = nextFk;
            ctxNk = nextNk;
        }

        return new AccountStatus(cashAmount, totalAssetAmount, List.copyOf(holdings));
    }

    @Override
    public PlaceOrderResult placeOrder(BrokerOrderRequest request) {
        if (request == null) {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR, "주문 요청이 null입니다.");
        }
        AccountIdentifier acct = parseAccount(request.brokerAccount());

        String side = request.side();
        if (!"buy".equals(side) && !"sell".equals(side)) {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR,
                    "지원하지 않는 side=" + side);
        }
        String orderType = request.orderType();
        String ordDvsn;
        if ("limit".equals(orderType)) {
            ordDvsn = "00";
        } else if ("market".equals(orderType)) {
            ordDvsn = "01";
        } else {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR,
                    "지원하지 않는 orderType=" + orderType);
        }

        BigDecimal qty = request.quantity();
        if (qty == null || qty.signum() <= 0) {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR,
                    "주문수량은 1 이상이어야 합니다.");
        }
        BigInteger qtyInt = qty.toBigInteger();
        if (qtyInt.signum() <= 0) {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR,
                    "주문수량은 1 이상이어야 합니다.");
        }

        String ordUnpr;
        if ("market".equals(orderType)) {
            ordUnpr = "0";
        } else {
            BigDecimal price = request.price();
            if (price == null || price.signum() <= 0) {
                throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR,
                        "지정가 주문은 price가 필요합니다.");
            }
            ordUnpr = price.toBigInteger().toString();
        }

        String symbol = request.symbolCode();
        if (symbol == null || symbol.isBlank()) {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR, "symbolCode 미지정");
        }

        boolean demo = isDemoEnvironment();
        String trId;
        String sllType;
        if ("buy".equals(side)) {
            trId = demo ? TR_ID_ORDER_BUY_DEMO : TR_ID_ORDER_BUY_REAL;
            sllType = "";
        } else {
            trId = demo ? TR_ID_ORDER_SELL_DEMO : TR_ID_ORDER_SELL_REAL;
            sllType = "01";
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("CANO", acct.cano());
        body.put("ACNT_PRDT_CD", acct.acntPrdtCd());
        body.put("PDNO", symbol);
        body.put("ORD_DVSN", ordDvsn);
        body.put("ORD_QTY", qtyInt.toString());
        body.put("ORD_UNPR", ordUnpr);
        body.put("EXCG_ID_DVSN_CD", "KRX");
        body.put("SLL_TYPE", sllType);
        body.put("CNDT_PRIC", "");

        String url = UriComponentsBuilder
                .fromUriString(resolveBaseUrl())
                .path(PATH_ORDER_CASH)
                .build()
                .toUriString();

        boolean tokenRotated = false;
        while (true) {
            ResponseEntity<String> response;
            String token = tokenService.getAccessToken();
            try {
                response = restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(h -> {
                            h.set("authorization", "Bearer " + token);
                            h.set("appkey", properties.getAppKey());
                            h.set("appsecret", properties.getAppSecret());
                            h.set("tr_id", trId);
                            h.set("custtype", "P");
                        })
                        .body(body)
                        .retrieve()
                        .toEntity(String.class);
            } catch (HttpClientErrorException ex) {
                throw classifyClientError(ex, "주문 제출");
            } catch (HttpServerErrorException ex) {
                throw new BrokerGatewayException(Category.TRANSIENT, VENDOR,
                        "KIS 주문 제출 5xx 응답 status=" + ex.getStatusCode().value(), ex);
            } catch (RestClientResponseException ex) {
                throw new BrokerGatewayException(Category.TRANSIENT, VENDOR,
                        "KIS 주문 제출 비정상 응답 status=" + ex.getStatusCode().value(), ex);
            } catch (ResourceAccessException ex) {
                throw classifyIoException(ex, "주문 제출");
            } catch (BrokerGatewayException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new BrokerGatewayException(Category.TRANSIENT, VENDOR,
                        "KIS 주문 제출 요청 실패: " + ex.getClass().getSimpleName(), ex);
            }

            JsonNode root = parseResponse(response, "주문 제출");
            String rtCd = KisResponseMapper.text(root.get("rt_cd")).trim();
            String msgCd = KisResponseMapper.text(root.get("msg_cd")).trim();
            String msg1 = KisResponseMapper.text(root.get("msg1"));

            if ("0".equals(rtCd)) {
                JsonNode output = root.get("output");
                String odno = output == null ? null
                        : KisResponseMapper.text(output.get("ODNO")).trim();
                if (odno == null || odno.isEmpty()) {
                    odno = null;
                }
                log.info("kis broker placeOrder ok tr_id={} rt_cd={} msg_cd={} odno={}",
                        trId, rtCd, msgCd, odno);
                return new PlaceOrderResult(
                        request.clientOrderId(),
                        odno,
                        PlaceOrderResult.STATUS_ACCEPTED,
                        BigDecimal.ZERO,
                        null,
                        null);
            }

            if (KisResponseMapper.isAuthError(msgCd, msg1) && !tokenRotated) {
                tokenService.invalidate();
                tokenRotated = true;
                continue;
            }
            if (KisResponseMapper.isAuthError(msgCd, msg1)) {
                log.warn("kis broker placeOrder auth_failed tr_id={} rt_cd={} msg_cd={}",
                        trId, rtCd, msgCd);
                throw new BrokerGatewayException(Category.AUTH_FAILED, VENDOR,
                        "KIS 주문 인증 실패 msg_cd=" + msgCd + " msg1=" + msg1);
            }

            // 도메인 결과: rejected (예외 아님).
            log.info("kis broker placeOrder rejected tr_id={} rt_cd={} msg_cd={}",
                    trId, rtCd, msgCd);
            String reason = (msgCd + " " + msg1).trim();
            return new PlaceOrderResult(
                    request.clientOrderId(),
                    null,
                    PlaceOrderResult.STATUS_REJECTED,
                    BigDecimal.ZERO,
                    null,
                    reason);
        }
    }

    @Override
    public OrderStatusResult getOrderStatus(BrokerAccountEntity brokerAccount, String brokerOrderNo) {
        if (brokerOrderNo == null || brokerOrderNo.isBlank()) {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR, "brokerOrderNo 미지정");
        }
        AccountIdentifier acct = parseAccount(brokerAccount);
        String trId = isDemoEnvironment() ? TR_ID_DAILY_CCLD_DEMO : TR_ID_DAILY_CCLD_REAL;
        String today = LocalDate.now(clock.withZone(KST)).format(DATE_FORMAT);

        String ctxFk = "";
        String ctxNk = "";
        boolean tokenRotated = false;
        String lastMsgCd = "";
        String lastMsg1 = "";

        while (true) {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(resolveBaseUrl())
                    .path(PATH_INQUIRE_DAILY_CCLD)
                    .queryParam("CANO", acct.cano())
                    .queryParam("ACNT_PRDT_CD", acct.acntPrdtCd())
                    .queryParam("INQR_STRT_DT", today)
                    .queryParam("INQR_END_DT", today)
                    .queryParam("SLL_BUY_DVSN_CD", "00")
                    .queryParam("PDNO", "")
                    .queryParam("CCLD_DVSN", "00")
                    .queryParam("INQR_DVSN", "00")
                    .queryParam("INQR_DVSN_3", "00")
                    .queryParam("ORD_GNO_BRNO", "")
                    .queryParam("ODNO", brokerOrderNo)
                    .queryParam("INQR_DVSN_1", "")
                    .queryParam("CTX_AREA_FK100", ctxFk)
                    .queryParam("CTX_AREA_NK100", ctxNk)
                    .queryParam("EXCG_ID_DVSN_CD", "KRX");
            String url = builder.build().toUriString();
            String trCont = (ctxFk.isEmpty() && ctxNk.isEmpty()) ? "" : "N";

            PageResult page;
            try {
                page = httpGet(url, trId, trCont, "주문 상태");
            } catch (BrokerGatewayException ex) {
                if (ex.getCategory() == Category.AUTH_FAILED && !tokenRotated) {
                    tokenService.invalidate();
                    tokenRotated = true;
                    continue;
                }
                throw ex;
            }

            JsonNode root = page.root();
            lastMsgCd = KisResponseMapper.text(root.get("msg_cd")).trim();
            lastMsg1 = KisResponseMapper.text(root.get("msg1"));
            JsonNode output1 = root.get("output1");
            if (output1 != null && output1.isArray()) {
                Iterator<JsonNode> it = output1.elements();
                while (it.hasNext()) {
                    JsonNode row = it.next();
                    String odno = KisResponseMapper.text(row.get("odno")).trim();
                    if (brokerOrderNo.equals(odno)) {
                        return toOrderStatus(row, lastMsgCd, lastMsg1);
                    }
                }
            }

            String responseTrCont = page.trCont() == null ? "" : page.trCont().trim();
            boolean morePages = "F".equals(responseTrCont) || "M".equals(responseTrCont);
            if (!morePages) {
                break;
            }
            String nextFk = KisResponseMapper.text(root.get("ctx_area_fk100")).trim();
            String nextNk = KisResponseMapper.text(root.get("ctx_area_nk100")).trim();
            if (nextFk.isEmpty() && nextNk.isEmpty()) {
                break;
            }
            ctxFk = nextFk;
            ctxNk = nextNk;
        }

        log.info("kis broker getOrderStatus order_not_found tr_id={} odno={} msg_cd={}",
                trId, brokerOrderNo, lastMsgCd);
        throw new BrokerGatewayException(Category.ORDER_NOT_FOUND, VENDOR,
                "ODNO " + brokerOrderNo);
    }

    // -----------------------------------------------------------------
    // 매핑 헬퍼
    // -----------------------------------------------------------------

    private HoldingItem toHoldingItem(JsonNode pos) {
        if (pos == null || pos.isNull()) {
            return null;
        }
        String symbolCode = KisResponseMapper.text(pos.get("pdno")).trim();
        if (symbolCode.isEmpty()) {
            return null;
        }
        BigDecimal qty = KisResponseMapper.parseQuantity(pos.get("hldg_qty"));
        if (qty == null || qty.signum() <= 0) {
            return null;
        }
        BigDecimal avgBuyPrice = KisResponseMapper.parseDecimal(pos.get("pchs_avg_pric"));
        return new HoldingItem(symbolCode, qty, avgBuyPrice == null ? BigDecimal.ZERO : avgBuyPrice);
    }

    private OrderStatusResult toOrderStatus(JsonNode row, String msgCd, String msg1) {
        BigDecimal ordQty = nullToZero(KisResponseMapper.parseQuantity(row.get("ord_qty")));
        BigDecimal totCcldQty = nullToZero(KisResponseMapper.parseQuantity(row.get("tot_ccld_qty")));
        BigDecimal rmnQty = nullToZero(KisResponseMapper.parseQuantity(row.get("rmn_qty")));
        BigDecimal rjctQty = nullToZero(KisResponseMapper.parseQuantity(row.get("rjct_qty")));
        String cnclYn = KisResponseMapper.text(row.get("cncl_yn")).trim();
        BigDecimal avgPrice = KisResponseMapper.parseDecimal(row.get("avg_prvs"));
        if (avgPrice != null && avgPrice.signum() == 0) {
            avgPrice = null;
        }
        String odno = KisResponseMapper.text(row.get("odno")).trim();

        String status;
        if ("Y".equalsIgnoreCase(cnclYn)) {
            status = "canceled";
        } else if (totCcldQty.signum() == 0 && rjctQty.signum() > 0) {
            status = PlaceOrderResult.STATUS_REJECTED;
        } else if (ordQty.signum() > 0 && totCcldQty.compareTo(ordQty) == 0) {
            status = PlaceOrderResult.STATUS_FILLED;
        } else if (totCcldQty.signum() > 0) {
            status = PlaceOrderResult.STATUS_PARTIAL;
        } else if (rmnQty.signum() > 0) {
            status = PlaceOrderResult.STATUS_ACCEPTED;
        } else {
            status = PlaceOrderResult.STATUS_FAILED;
        }

        String failureReason = null;
        if (PlaceOrderResult.STATUS_REJECTED.equals(status)
                || PlaceOrderResult.STATUS_FAILED.equals(status)) {
            failureReason = (msgCd + " " + msg1).trim();
            if (failureReason.isEmpty()) {
                failureReason = null;
            }
        }
        return new OrderStatusResult(odno, status, totCcldQty, avgPrice, failureReason);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // -----------------------------------------------------------------
    // HTTP helpers
    // -----------------------------------------------------------------

    private PageResult httpGet(String url, String trId, String trCont, String label) {
        String token = tokenService.getAccessToken();
        ResponseEntity<String> response;
        Map<String, String> extra = trCont == null ? Map.of() : Map.of("tr_cont", trCont);
        try {
            response = restClient.get()
                    .uri(url)
                    .headers(headers -> {
                        headers.set("authorization", "Bearer " + token);
                        headers.set("appkey", properties.getAppKey());
                        headers.set("appsecret", properties.getAppSecret());
                        headers.set("tr_id", trId);
                        headers.set("custtype", "P");
                        for (Map.Entry<String, String> e : extra.entrySet()) {
                            headers.set(e.getKey(), e.getValue());
                        }
                    })
                    .retrieve()
                    .toEntity(String.class);
        } catch (HttpClientErrorException ex) {
            throw classifyClientError(ex, label);
        } catch (HttpServerErrorException ex) {
            throw new BrokerGatewayException(Category.TRANSIENT, VENDOR,
                    "KIS " + label + " 5xx 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (RestClientResponseException ex) {
            throw new BrokerGatewayException(Category.TRANSIENT, VENDOR,
                    "KIS " + label + " 비정상 응답 status=" + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw classifyIoException(ex, label);
        } catch (BrokerGatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BrokerGatewayException(Category.TRANSIENT, VENDOR,
                    "KIS " + label + " 요청 실패: " + ex.getClass().getSimpleName(), ex);
        }

        JsonNode root = parseResponse(response, label);
        String rtCd = KisResponseMapper.text(root.get("rt_cd")).trim();
        String msgCd = KisResponseMapper.text(root.get("msg_cd")).trim();
        String msg1 = KisResponseMapper.text(root.get("msg1"));
        Category errorCategory = KisResponseMapper.classifyKisError(rtCd, msgCd, msg1);
        if (errorCategory != null) {
            log.warn("kis broker {} kis_error tr_id={} rt_cd={} msg_cd={}",
                    label, trId, rtCd, msgCd);
            throw new BrokerGatewayException(errorCategory, VENDOR,
                    "KIS " + label + " 오류 msg_cd=" + msgCd + " msg1=" + msg1);
        }
        List<String> trContHeader = response.getHeaders().get("tr_cont");
        String trContResp = (trContHeader == null || trContHeader.isEmpty())
                ? "" : trContHeader.get(0);
        return new PageResult(root, trContResp);
    }

    private JsonNode parseResponse(ResponseEntity<String> response, String label) {
        HttpStatusCode statusCode = response.getStatusCode();
        if (!statusCode.is2xxSuccessful()) {
            throw new BrokerGatewayException(Category.TRANSIENT, VENDOR,
                    "KIS " + label + " HTTP 비정상 응답 status=" + statusCode.value());
        }
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new BrokerGatewayException(Category.EMPTY_RESPONSE, VENDOR,
                    "KIS " + label + " 응답 본문이 비어 있다");
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new BrokerGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS " + label + " 응답 JSON 파싱 실패", ex);
        }
    }

    private static BrokerGatewayException classifyClientError(HttpClientErrorException ex, String label) {
        int status = ex.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new BrokerGatewayException(Category.AUTH_FAILED, VENDOR,
                    "KIS " + label + " 인증 실패 status=" + status, ex);
        }
        if (status == 429) {
            return new BrokerGatewayException(Category.TRANSIENT, VENDOR,
                    "KIS " + label + " 요청 제한 status=" + status, ex);
        }
        if (status == 400 || status == 404 || status == 422) {
            return new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR,
                    "KIS " + label + " 요청 오류 status=" + status, ex);
        }
        return new BrokerGatewayException(Category.TRANSIENT, VENDOR,
                "KIS " + label + " 4xx 응답 status=" + status, ex);
    }

    private static BrokerGatewayException classifyIoException(ResourceAccessException ex, String label) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return new BrokerGatewayException(Category.TIMEOUT, VENDOR,
                        "KIS " + label + " 타임아웃", ex);
            }
            cause = cause.getCause();
        }
        return new BrokerGatewayException(Category.TRANSIENT, VENDOR,
                "KIS " + label + " 네트워크 오류", ex);
    }

    // -----------------------------------------------------------------
    // 계좌번호 파싱
    // -----------------------------------------------------------------

    private static AccountIdentifier parseAccount(BrokerAccountEntity brokerAccount) {
        if (brokerAccount == null) {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR,
                    "brokerAccount가 null입니다.");
        }
        String raw = brokerAccount.getBrokerAccountNo();
        if (raw == null || raw.isBlank()) {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR,
                    "brokerAccountNo 미설정");
        }
        int dash = raw.indexOf('-');
        if (dash <= 0 || dash >= raw.length() - 1) {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR,
                    "brokerAccountNo 형식 오류 (CANO-ACNT_PRDT_CD 기대)");
        }
        String cano = raw.substring(0, dash).trim();
        String prdt = raw.substring(dash + 1).trim();
        if (cano.isEmpty() || prdt.isEmpty()) {
            throw new BrokerGatewayException(Category.REQUEST_INVALID, VENDOR,
                    "brokerAccountNo 형식 오류");
        }
        return new AccountIdentifier(cano, prdt);
    }

    private record AccountIdentifier(String cano, String acntPrdtCd) {
    }

    private record PageResult(JsonNode root, String trCont) {
    }
}
