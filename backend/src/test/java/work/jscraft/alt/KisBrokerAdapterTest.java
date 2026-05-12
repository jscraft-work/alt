package work.jscraft.alt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import work.jscraft.alt.integrations.kis.KisAccessTokenService;
import work.jscraft.alt.integrations.kis.KisProperties;
import work.jscraft.alt.integrations.kis.broker.KisBrokerAdapter;
import work.jscraft.alt.integrations.kis.broker.KisResponseMapper;
import work.jscraft.alt.strategy.infrastructure.persistence.BrokerAccountEntity;
import work.jscraft.alt.trading.application.broker.AccountStatus;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException.Category;
import work.jscraft.alt.trading.application.broker.BrokerOrderRequest;
import work.jscraft.alt.trading.application.broker.OrderStatusResult;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisBrokerAdapterTest {

    private static final String APP_KEY = "kis-app-key";
    private static final String APP_SECRET = "kis-app-secret";
    private static final String ACCOUNT_NO = "72419398-01";
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    private static final String PATH_BALANCE = "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String PATH_ORDER = "/uapi/domestic-stock/v1/trading/order-cash";
    private static final String PATH_CCLD = "/uapi/domestic-stock/v1/trading/inquire-daily-ccld";

    private HttpServer server;
    private int port;
    // 2026-05-12 12:00 KST. Today (KST) = 2026-05-12.
    private final Instant fixedInstant = Instant.parse("2026-05-12T03:00:00Z");
    private final Clock clock = Clock.fixed(fixedInstant, KST_ZONE);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // =================================================================
    // getAccountStatus
    // =================================================================

    @Test
    void getAccountStatusHappySinglePageReturnsMappedSummaryAndHoldings() {
        AtomicReference<RequestCapture> captured = new AtomicReference<>();
        String body = """
                {
                  "rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                  "output1":[
                    {"pdno":"005930","hldg_qty":"12","pchs_avg_pric":"81200"},
                    {"pdno":"000660","hldg_qty":"4","pchs_avg_pric":"120000"}
                  ],
                  "output2":[
                    {"dnca_tot_amt":"6200000","tot_evlu_amt":"10120000"}
                  ],
                  "ctx_area_fk100":"","ctx_area_nk100":""
                }
                """;
        server.createContext(PATH_BALANCE, recorder(captured, 200, body, ""));

        CountingTokenService tokens = new CountingTokenService("tok-1");
        KisBrokerAdapter adapter = newAdapter(tokens);

        AccountStatus status = adapter.getAccountStatus(account(ACCOUNT_NO));

        assertThat(status.cashAmount()).isEqualByComparingTo("6200000");
        assertThat(status.totalAssetAmount()).isEqualByComparingTo("10120000");
        assertThat(status.holdings()).hasSize(2);
        assertThat(status.holdings().get(0).symbolCode()).isEqualTo("005930");
        assertThat(status.holdings().get(0).quantity()).isEqualByComparingTo("12");
        assertThat(status.holdings().get(0).avgBuyPrice()).isEqualByComparingTo("81200");

        RequestCapture cap = captured.get();
        assertThat(cap).isNotNull();
        assertThat(cap.headers.get("authorization")).isEqualTo("Bearer tok-1");
        assertThat(cap.headers.get("appkey")).isEqualTo(APP_KEY);
        assertThat(cap.headers.get("appsecret")).isEqualTo(APP_SECRET);
        assertThat(cap.headers.get("tr_id")).isEqualTo("TTTC8434R");
        assertThat(cap.headers.get("custtype")).isEqualTo("P");
        assertThat(cap.query).contains("CANO=72419398");
        assertThat(cap.query).contains("ACNT_PRDT_CD=01");
        assertThat(cap.query).contains("AFHR_FLPR_YN=N");
        assertThat(cap.query).contains("INQR_DVSN=02");
        assertThat(cap.query).contains("UNPR_DVSN=01");
        assertThat(cap.query).contains("FUND_STTL_ICLD_YN=N");
        assertThat(cap.query).contains("FNCG_AMT_AUTO_RDPT_YN=N");
        assertThat(cap.query).contains("PRCS_DVSN=00");
        assertThat(tokens.invalidateCount.get()).isEqualTo(0);
    }

    @Test
    void getAccountStatusDemoEnvironmentSendsDemoTrId() {
        AtomicReference<RequestCapture> captured = new AtomicReference<>();
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[],
                 "output2":[{"dnca_tot_amt":"1","tot_evlu_amt":"2"}],
                 "ctx_area_fk100":"","ctx_area_nk100":""}
                """;
        server.createContext(PATH_BALANCE, recorder(captured, 200, body, ""));

        KisProperties props = baseProperties();
        props.setEnvironment("demo");
        KisBrokerAdapter adapter = newAdapter(props, new CountingTokenService("tok-x"));

        adapter.getAccountStatus(account(ACCOUNT_NO));

        assertThat(captured.get().headers.get("tr_id")).isEqualTo("VTTC8434R");
    }

    @Test
    void getAccountStatusPaginatesAndUsesLastPageSummary() {
        AtomicInteger calls = new AtomicInteger();
        String page1 = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[{"pdno":"005930","hldg_qty":"12","pchs_avg_pric":"81200"}],
                 "output2":[{"dnca_tot_amt":"100","tot_evlu_amt":"200"}],
                 "ctx_area_fk100":"FK1","ctx_area_nk100":"NK1"}
                """;
        String page2 = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[{"pdno":"000660","hldg_qty":"4","pchs_avg_pric":"120000"}],
                 "output2":[{"dnca_tot_amt":"6200000","tot_evlu_amt":"10120000"}],
                 "ctx_area_fk100":"","ctx_area_nk100":""}
                """;
        server.createContext(PATH_BALANCE, exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                respondJson(exchange, 200, page1, "F");
            } else {
                respondJson(exchange, 200, page2, "D");
            }
        });

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-pg"));

        AccountStatus status = adapter.getAccountStatus(account(ACCOUNT_NO));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(status.holdings()).hasSize(2);
        assertThat(status.cashAmount()).isEqualByComparingTo("6200000");
        assertThat(status.totalAssetAmount()).isEqualByComparingTo("10120000");
    }

    @Test
    void getAccountStatusRotatesTokenOnceOnAuthError() {
        AtomicInteger calls = new AtomicInteger();
        String authBody = """
                {"rt_cd":"1","msg_cd":"EGW00121","msg1":"토큰이 만료되었습니다.",
                 "output1":[],"output2":[],"ctx_area_fk100":"","ctx_area_nk100":""}
                """;
        String okBody = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[{"pdno":"005930","hldg_qty":"1","pchs_avg_pric":"100"}],
                 "output2":[{"dnca_tot_amt":"1","tot_evlu_amt":"2"}],
                 "ctx_area_fk100":"","ctx_area_nk100":""}
                """;
        server.createContext(PATH_BALANCE, exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                respondJson(exchange, 200, authBody, "");
            } else {
                respondJson(exchange, 200, okBody, "");
            }
        });

        CountingTokenService tokens = new CountingTokenService("tok-rot");
        KisBrokerAdapter adapter = newAdapter(tokens);

        AccountStatus status = adapter.getAccountStatus(account(ACCOUNT_NO));

        assertThat(status.holdings()).hasSize(1);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(tokens.invalidateCount.get()).isEqualTo(1);
    }

    @Test
    void getAccountStatusPersistentAuthFailureThrowsAuthFailed() {
        String authBody = """
                {"rt_cd":"1","msg_cd":"EGW00121","msg1":"토큰이 만료되었습니다.",
                 "output1":[],"output2":[]}
                """;
        server.createContext(PATH_BALANCE, exchange -> respondJson(exchange, 200, authBody, ""));

        CountingTokenService tokens = new CountingTokenService("tok-stuck");
        KisBrokerAdapter adapter = newAdapter(tokens);

        assertThatThrownBy(() -> adapter.getAccountStatus(account(ACCOUNT_NO)))
                .isInstanceOf(BrokerGatewayException.class)
                .satisfies(ex -> assertThat(((BrokerGatewayException) ex).getCategory())
                        .isEqualTo(Category.AUTH_FAILED));
        assertThat(tokens.invalidateCount.get()).isEqualTo(1);
    }

    @Test
    void getAccountStatusTransientKisErrorThrows() {
        String body = """
                {"rt_cd":"1","msg_cd":"MCA00001","msg1":"기타 오류",
                 "output1":[],"output2":[]}
                """;
        server.createContext(PATH_BALANCE, exchange -> respondJson(exchange, 200, body, ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        assertThatThrownBy(() -> adapter.getAccountStatus(account(ACCOUNT_NO)))
                .isInstanceOf(BrokerGatewayException.class)
                .satisfies(ex -> assertThat(((BrokerGatewayException) ex).getCategory())
                        .isEqualTo(Category.TRANSIENT));
    }

    @Test
    void getAccountStatusMalformedAccountNumberThrowsRequestInvalidNoHttp() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext(PATH_BALANCE, exchange -> {
            calls.incrementAndGet();
            respondJson(exchange, 200, "{}", "");
        });

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        assertThatThrownBy(() -> adapter.getAccountStatus(account("badformat")))
                .isInstanceOf(BrokerGatewayException.class)
                .satisfies(ex -> assertThat(((BrokerGatewayException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
        assertThat(calls.get()).isEqualTo(0);
    }

    @Test
    void getAccountStatusFiltersZeroQuantityHoldings() {
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[
                   {"pdno":"005930","hldg_qty":"0","pchs_avg_pric":"81200"},
                   {"pdno":"000660","hldg_qty":"10","pchs_avg_pric":"120000"}
                 ],
                 "output2":[{"dnca_tot_amt":"100","tot_evlu_amt":"200"}],
                 "ctx_area_fk100":"","ctx_area_nk100":""}
                """;
        server.createContext(PATH_BALANCE, exchange -> respondJson(exchange, 200, body, ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-z"));

        AccountStatus status = adapter.getAccountStatus(account(ACCOUNT_NO));

        assertThat(status.holdings()).hasSize(1);
        assertThat(status.holdings().get(0).symbolCode()).isEqualTo("000660");
    }

    // =================================================================
    // placeOrder
    // =================================================================

    @Test
    void placeOrderHappyBuyMarket() {
        AtomicReference<RequestCapture> captured = new AtomicReference<>();
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output":{"KRX_FWDG_ORD_ORGNO":"00","ODNO":"00012345","ORD_TMD":"100000"}}
                """;
        server.createContext(PATH_ORDER, recorderWithBody(captured, 200, body));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-o"));

        BrokerOrderRequest req = new BrokerOrderRequest(
                account(ACCOUNT_NO), "cli-1", "005930", "buy",
                new BigDecimal("10"), "market", null);
        PlaceOrderResult result = adapter.placeOrder(req);

        assertThat(result.clientOrderId()).isEqualTo("cli-1");
        assertThat(result.brokerOrderNo()).isEqualTo("00012345");
        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_ACCEPTED);
        assertThat(result.filledQuantity()).isEqualByComparingTo("0");
        assertThat(result.avgFilledPrice()).isNull();
        assertThat(result.failureReason()).isNull();

        RequestCapture cap = captured.get();
        assertThat(cap.method).isEqualTo("POST");
        assertThat(cap.headers.get("tr_id")).isEqualTo("TTTC0012U");
        assertThat(cap.headers.get("authorization")).isEqualTo("Bearer tok-o");
        JsonNode payload = parseJson(cap.body);
        assertThat(payload.path("CANO").asText()).isEqualTo("72419398");
        assertThat(payload.path("ACNT_PRDT_CD").asText()).isEqualTo("01");
        assertThat(payload.path("PDNO").asText()).isEqualTo("005930");
        assertThat(payload.path("ORD_DVSN").asText()).isEqualTo("01");
        assertThat(payload.path("ORD_QTY").asText()).isEqualTo("10");
        assertThat(payload.path("ORD_UNPR").asText()).isEqualTo("0");
        assertThat(payload.path("EXCG_ID_DVSN_CD").asText()).isEqualTo("KRX");
        assertThat(payload.path("SLL_TYPE").asText()).isEqualTo("");
        assertThat(payload.path("CNDT_PRIC").asText()).isEqualTo("");
    }

    @Test
    void placeOrderHappySellLimitSendsSellTrIdAndSllType() {
        AtomicReference<RequestCapture> captured = new AtomicReference<>();
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output":{"ODNO":"00099999"}}
                """;
        server.createContext(PATH_ORDER, recorderWithBody(captured, 200, body));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-sell"));

        BrokerOrderRequest req = new BrokerOrderRequest(
                account(ACCOUNT_NO), "cli-2", "005930", "sell",
                new BigDecimal("3"), "limit", new BigDecimal("70000"));
        PlaceOrderResult result = adapter.placeOrder(req);

        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_ACCEPTED);
        assertThat(result.brokerOrderNo()).isEqualTo("00099999");

        RequestCapture cap = captured.get();
        assertThat(cap.headers.get("tr_id")).isEqualTo("TTTC0011U");
        JsonNode payload = parseJson(cap.body);
        assertThat(payload.path("ORD_DVSN").asText()).isEqualTo("00");
        assertThat(payload.path("ORD_UNPR").asText()).isEqualTo("70000");
        assertThat(payload.path("SLL_TYPE").asText()).isEqualTo("01");
    }

    @Test
    void placeOrderDemoBuyUsesDemoTrId() {
        AtomicReference<RequestCapture> captured = new AtomicReference<>();
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK","output":{"ODNO":"D-1"}}
                """;
        server.createContext(PATH_ORDER, recorderWithBody(captured, 200, body));

        KisProperties props = baseProperties();
        props.setEnvironment("demo");
        KisBrokerAdapter adapter = newAdapter(props, new CountingTokenService("tok-demo"));

        BrokerOrderRequest req = new BrokerOrderRequest(
                account(ACCOUNT_NO), "cli-d", "005930", "buy",
                new BigDecimal("1"), "market", null);
        adapter.placeOrder(req);

        assertThat(captured.get().headers.get("tr_id")).isEqualTo("VTTC0012U");
    }

    @Test
    void placeOrderKisRejectionReturnsRejectedDoesNotThrow() {
        String body = """
                {"rt_cd":"1","msg_cd":"OTHER","msg1":"잔고부족","output":{}}
                """;
        server.createContext(PATH_ORDER, exchange -> respondJson(exchange, 200, body, ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-rej"));

        BrokerOrderRequest req = new BrokerOrderRequest(
                account(ACCOUNT_NO), "cli-rej", "005930", "buy",
                new BigDecimal("5"), "market", null);
        PlaceOrderResult result = adapter.placeOrder(req);

        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_REJECTED);
        assertThat(result.brokerOrderNo()).isNull();
        assertThat(result.failureReason()).contains("OTHER").contains("잔고부족");
    }

    @Test
    void placeOrderRotatesTokenOnAuthError() {
        AtomicInteger calls = new AtomicInteger();
        String authBody = """
                {"rt_cd":"1","msg_cd":"EGW00121","msg1":"토큰이 만료되었습니다.","output":{}}
                """;
        String okBody = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK","output":{"ODNO":"AFT-ROT"}}
                """;
        server.createContext(PATH_ORDER, exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                respondJson(exchange, 200, authBody, "");
            } else {
                respondJson(exchange, 200, okBody, "");
            }
        });

        CountingTokenService tokens = new CountingTokenService("tok-rot-o");
        KisBrokerAdapter adapter = newAdapter(tokens);

        BrokerOrderRequest req = new BrokerOrderRequest(
                account(ACCOUNT_NO), "cli-rot", "005930", "buy",
                new BigDecimal("1"), "market", null);
        PlaceOrderResult result = adapter.placeOrder(req);

        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_ACCEPTED);
        assertThat(result.brokerOrderNo()).isEqualTo("AFT-ROT");
        assertThat(calls.get()).isEqualTo(2);
        assertThat(tokens.invalidateCount.get()).isEqualTo(1);
    }

    @Test
    void placeOrderInvalidOrderTypeThrowsRequestInvalidNoHttp() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext(PATH_ORDER, exchange -> {
            calls.incrementAndGet();
            respondJson(exchange, 200, "{}", "");
        });

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        BrokerOrderRequest req = new BrokerOrderRequest(
                account(ACCOUNT_NO), "cli-bad", "005930", "buy",
                new BigDecimal("1"), "iceberg", new BigDecimal("70000"));
        assertThatThrownBy(() -> adapter.placeOrder(req))
                .isInstanceOf(BrokerGatewayException.class)
                .satisfies(ex -> assertThat(((BrokerGatewayException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
        assertThat(calls.get()).isEqualTo(0);
    }

    @Test
    void placeOrderInvalidSideThrowsRequestInvalid() {
        server.createContext(PATH_ORDER, exchange -> respondJson(exchange, 200, "{}", ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        BrokerOrderRequest req = new BrokerOrderRequest(
                account(ACCOUNT_NO), "cli-bad", "005930", "hedge",
                new BigDecimal("1"), "market", null);
        assertThatThrownBy(() -> adapter.placeOrder(req))
                .isInstanceOf(BrokerGatewayException.class)
                .satisfies(ex -> assertThat(((BrokerGatewayException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
    }

    @Test
    void placeOrderZeroQuantityThrowsRequestInvalid() {
        server.createContext(PATH_ORDER, exchange -> respondJson(exchange, 200, "{}", ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        BrokerOrderRequest req = new BrokerOrderRequest(
                account(ACCOUNT_NO), "cli-bad", "005930", "buy",
                BigDecimal.ZERO, "market", null);
        assertThatThrownBy(() -> adapter.placeOrder(req))
                .isInstanceOf(BrokerGatewayException.class)
                .satisfies(ex -> assertThat(((BrokerGatewayException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
    }

    @Test
    void placeOrderHttp500ThrowsTransient() {
        server.createContext(PATH_ORDER, exchange -> {
            byte[] payload = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        BrokerOrderRequest req = new BrokerOrderRequest(
                account(ACCOUNT_NO), "cli-500", "005930", "buy",
                new BigDecimal("1"), "market", null);
        assertThatThrownBy(() -> adapter.placeOrder(req))
                .isInstanceOf(BrokerGatewayException.class)
                .satisfies(ex -> assertThat(((BrokerGatewayException) ex).getCategory())
                        .isEqualTo(Category.TRANSIENT));
    }

    @Test
    void placeOrderTimeoutThrowsTimeout() {
        server.createContext(PATH_ORDER, exchange -> {
            try {
                Thread.sleep(2_500L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(200, 0);
            } catch (IOException ignored) {
                // client gone
            } finally {
                exchange.close();
            }
        });

        KisProperties props = baseProperties();
        props.setRestTimeoutSeconds(1);
        KisBrokerAdapter adapter = newAdapter(props, new CountingTokenService("tok-x"));

        BrokerOrderRequest req = new BrokerOrderRequest(
                account(ACCOUNT_NO), "cli-to", "005930", "buy",
                new BigDecimal("1"), "market", null);

        Instant start = Instant.now();
        assertThatThrownBy(() -> adapter.placeOrder(req))
                .isInstanceOf(BrokerGatewayException.class)
                .satisfies(ex -> assertThat(((BrokerGatewayException) ex).getCategory())
                        .isEqualTo(Category.TIMEOUT));
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(3));
    }

    // =================================================================
    // getOrderStatus
    // =================================================================

    @Test
    void getOrderStatusFullyFilledMapsFilled() {
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[{"odno":"00012345","ord_qty":"10","tot_ccld_qty":"10",
                              "avg_prvs":"70050","rmn_qty":"0","cncl_yn":"N","rjct_qty":"0"}],
                 "output2":{},"ctx_area_fk100":"","ctx_area_nk100":""}
                """;
        server.createContext(PATH_CCLD, exchange -> respondJson(exchange, 200, body, ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-st"));

        OrderStatusResult result = adapter.getOrderStatus(account(ACCOUNT_NO), "00012345");

        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_FILLED);
        assertThat(result.filledQuantity()).isEqualByComparingTo("10");
        assertThat(result.avgFilledPrice()).isEqualByComparingTo("70050");
        assertThat(result.failureReason()).isNull();
        assertThat(result.brokerOrderNo()).isEqualTo("00012345");
    }

    @Test
    void getOrderStatusPartialFill() {
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[{"odno":"00012345","ord_qty":"10","tot_ccld_qty":"3",
                              "avg_prvs":"70000","rmn_qty":"7","cncl_yn":"N","rjct_qty":"0"}],
                 "output2":{},"ctx_area_fk100":"","ctx_area_nk100":""}
                """;
        server.createContext(PATH_CCLD, exchange -> respondJson(exchange, 200, body, ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-pt"));

        OrderStatusResult result = adapter.getOrderStatus(account(ACCOUNT_NO), "00012345");

        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_PARTIAL);
        assertThat(result.filledQuantity()).isEqualByComparingTo("3");
        assertThat(result.avgFilledPrice()).isEqualByComparingTo("70000");
    }

    @Test
    void getOrderStatusAcceptedOnly() {
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[{"odno":"00012345","ord_qty":"10","tot_ccld_qty":"0",
                              "avg_prvs":"0","rmn_qty":"10","cncl_yn":"N","rjct_qty":"0"}],
                 "output2":{}}
                """;
        server.createContext(PATH_CCLD, exchange -> respondJson(exchange, 200, body, ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-ac"));

        OrderStatusResult result = adapter.getOrderStatus(account(ACCOUNT_NO), "00012345");

        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_ACCEPTED);
        assertThat(result.filledQuantity()).isEqualByComparingTo("0");
        assertThat(result.avgFilledPrice()).isNull();
    }

    @Test
    void getOrderStatusCanceled() {
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[{"odno":"00012345","ord_qty":"10","tot_ccld_qty":"0",
                              "avg_prvs":"0","rmn_qty":"0","cncl_yn":"Y","rjct_qty":"0"}],
                 "output2":{}}
                """;
        server.createContext(PATH_CCLD, exchange -> respondJson(exchange, 200, body, ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-cx"));

        OrderStatusResult result = adapter.getOrderStatus(account(ACCOUNT_NO), "00012345");

        assertThat(result.orderStatus()).isEqualTo("canceled");
        assertThat(result.isTerminal()).isTrue();
    }

    @Test
    void getOrderStatusRejected() {
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[{"odno":"00012345","ord_qty":"10","tot_ccld_qty":"0",
                              "avg_prvs":"0","rmn_qty":"0","cncl_yn":"N","rjct_qty":"10"}],
                 "output2":{}}
                """;
        server.createContext(PATH_CCLD, exchange -> respondJson(exchange, 200, body, ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-rj"));

        OrderStatusResult result = adapter.getOrderStatus(account(ACCOUNT_NO), "00012345");

        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_REJECTED);
        assertThat(result.failureReason()).isNotNull();
    }

    @Test
    void getOrderStatusOrderNotFoundThrows() {
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[],"output2":{},"ctx_area_fk100":"","ctx_area_nk100":""}
                """;
        server.createContext(PATH_CCLD, exchange -> respondJson(exchange, 200, body, ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-nf"));

        assertThatThrownBy(() -> adapter.getOrderStatus(account(ACCOUNT_NO), "99999999"))
                .isInstanceOf(BrokerGatewayException.class)
                .satisfies(ex -> assertThat(((BrokerGatewayException) ex).getCategory())
                        .isEqualTo(Category.ORDER_NOT_FOUND));
    }

    @Test
    void getOrderStatusPaginatesAcrossPages() {
        AtomicInteger calls = new AtomicInteger();
        String page1 = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[{"odno":"AAA","ord_qty":"1","tot_ccld_qty":"1",
                              "avg_prvs":"100","rmn_qty":"0","cncl_yn":"N","rjct_qty":"0"}],
                 "output2":{},"ctx_area_fk100":"FK","ctx_area_nk100":"NK"}
                """;
        String page2 = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[{"odno":"00012345","ord_qty":"10","tot_ccld_qty":"10",
                              "avg_prvs":"70000","rmn_qty":"0","cncl_yn":"N","rjct_qty":"0"}],
                 "output2":{},"ctx_area_fk100":"","ctx_area_nk100":""}
                """;
        server.createContext(PATH_CCLD, exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                respondJson(exchange, 200, page1, "F");
            } else {
                respondJson(exchange, 200, page2, "D");
            }
        });

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("tok-pg"));

        OrderStatusResult result = adapter.getOrderStatus(account(ACCOUNT_NO), "00012345");

        assertThat(calls.get()).isEqualTo(2);
        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_FILLED);
        assertThat(result.brokerOrderNo()).isEqualTo("00012345");
    }

    // =================================================================
    // General
    // =================================================================

    @Test
    void bearerTokenIsPresentOnRequests() {
        AtomicReference<RequestCapture> captured = new AtomicReference<>();
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK",
                 "output1":[],"output2":[{"dnca_tot_amt":"0","tot_evlu_amt":"0"}],
                 "ctx_area_fk100":"","ctx_area_nk100":""}
                """;
        server.createContext(PATH_BALANCE, recorder(captured, 200, body, ""));

        KisBrokerAdapter adapter = newAdapter(new CountingTokenService("bearer-token-1"));

        adapter.getAccountStatus(account(ACCOUNT_NO));

        assertThat(captured.get().headers.get("authorization")).isEqualTo("Bearer bearer-token-1");
    }

    // =================================================================
    // helpers
    // =================================================================

    private KisBrokerAdapter newAdapter(CountingTokenService tokens) {
        return newAdapter(baseProperties(), tokens);
    }

    private KisBrokerAdapter newAdapter(KisProperties props, CountingTokenService tokens) {
        Duration timeout = Duration.ofSeconds(Math.max(1, props.getRestTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        ObjectMapper mapper = new ObjectMapper();
        KisResponseMapper responseMapper = new KisResponseMapper(mapper);
        return new KisBrokerAdapter(props, tokens, restClient, mapper, responseMapper, clock);
    }

    private KisProperties baseProperties() {
        KisProperties p = new KisProperties();
        p.setAppKey(APP_KEY);
        p.setAppSecret(APP_SECRET);
        p.setBaseUrl("http://127.0.0.1:" + port);
        p.setRestTimeoutSeconds(5);
        p.setEnvironment("real");
        return p;
    }

    private BrokerAccountEntity account(String brokerAccountNo) {
        BrokerAccountEntity entity = new BrokerAccountEntity();
        entity.setBrokerCode("KIS");
        entity.setBrokerAccountNo(brokerAccountNo);
        entity.setAccountMasked("masked");
        return entity;
    }

    private JsonNode parseJson(String body) {
        try {
            return new ObjectMapper().readTree(body == null ? "{}" : body);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void respondJson(HttpExchange exchange, int status, String body, String trCont)
            throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (trCont != null && !trCont.isEmpty()) {
            exchange.getResponseHeaders().add("tr_cont", trCont);
        }
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private static HttpHandler recorder(AtomicReference<RequestCapture> captured,
                                        int status,
                                        String body,
                                        String trCont) {
        return (HttpExchange exchange) -> {
            captureRequest(captured, exchange, false);
            respondJson(exchange, status, body, trCont);
        };
    }

    private static HttpHandler recorderWithBody(AtomicReference<RequestCapture> captured,
                                                int status,
                                                String body) {
        return (HttpExchange exchange) -> {
            captureRequest(captured, exchange, true);
            respondJson(exchange, status, body, "");
        };
    }

    private static void captureRequest(AtomicReference<RequestCapture> captured,
                                       HttpExchange exchange,
                                       boolean readBody) throws IOException {
        RequestCapture cap = new RequestCapture();
        cap.method = exchange.getRequestMethod();
        for (String name : exchange.getRequestHeaders().keySet()) {
            cap.headers.put(name.toLowerCase(Locale.ROOT),
                    exchange.getRequestHeaders().getFirst(name));
        }
        cap.query = exchange.getRequestURI().getRawQuery();
        if (readBody) {
            try (InputStream in = exchange.getRequestBody();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                in.transferTo(baos);
                cap.body = baos.toString(StandardCharsets.UTF_8);
            }
        }
        captured.set(cap);
    }

    private static final class RequestCapture {
        private final Map<String, String> headers = new HashMap<>();
        private String method;
        private String query;
        private String body;
    }

    /** Test token service that returns a fixed token and counts invalidate() calls. */
    private static final class CountingTokenService extends KisAccessTokenService {
        private final String token;
        private final AtomicInteger invalidateCount = new AtomicInteger();
        // mark referenced to silence unused field warning if any
        @SuppressWarnings("unused")
        private final AtomicBoolean used = new AtomicBoolean();

        CountingTokenService(String token) {
            super(stubProperties(), null, new ObjectMapper(),
                    RestClient.builder().build());
            this.token = token;
        }

        private static KisProperties stubProperties() {
            KisProperties p = new KisProperties();
            p.setAppKey("stub");
            p.setAppSecret("stub");
            return p;
        }

        @Override
        public String getAccessToken() {
            return token;
        }

        @Override
        public void invalidate() {
            invalidateCount.incrementAndGet();
        }
    }
}
