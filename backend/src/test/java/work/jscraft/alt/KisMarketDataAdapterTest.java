package work.jscraft.alt;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import work.jscraft.alt.integrations.kis.KisAccessTokenService;
import work.jscraft.alt.integrations.kis.KisProperties;
import work.jscraft.alt.integrations.kis.marketdata.KisMarketDataAdapter;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.application.MarketDataException.Category;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.MinuteBar;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.OrderBookSnapshot;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.PriceSnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisMarketDataAdapterTest {

    private static final String APP_KEY = "kis-app-key";
    private static final String APP_SECRET = "kis-app-secret";
    private static final String SYMBOL = "005930";
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    private HttpServer server;
    private int port;
    // 2026-05-12 12:00 KST. Today (KST) = 2026-05-12.
    private final Instant fixedInstant = Instant.parse("2026-05-12T03:00:00Z");
    private final Clock clock = Clock.fixed(fixedInstant, KST_ZONE);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // -----------------------------------------------------------------
    // fetchPrice
    // -----------------------------------------------------------------

    @Test
    void fetchPriceHappyReturnsMappedSnapshot() {
        AtomicReference<RequestCapture> captured = new AtomicReference<>();
        String body = """
                {
                  "rt_cd": "0",
                  "msg_cd": "MCA00000",
                  "msg1": "정상처리 되었습니다.",
                  "output": {
                    "stck_prpr": "70000",
                    "stck_oprc": "69500",
                    "stck_hgpr": "70800",
                    "stck_lwpr": "69200",
                    "acml_vol": "12345678"
                  }
                }
                """;
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-price",
                recorder(captured, 200, body, ""));

        CountingTokenService tokens = new CountingTokenService("tok-1");
        KisMarketDataAdapter adapter = newAdapter(tokens);

        PriceSnapshot snapshot = adapter.fetchPrice(SYMBOL);

        assertThat(snapshot.symbolCode()).isEqualTo(SYMBOL);
        assertThat(snapshot.sourceName()).isEqualTo("kis");
        assertThat(snapshot.lastPrice()).isEqualByComparingTo(new BigDecimal("70000"));
        assertThat(snapshot.openPrice()).isEqualByComparingTo(new BigDecimal("69500"));
        assertThat(snapshot.highPrice()).isEqualByComparingTo(new BigDecimal("70800"));
        assertThat(snapshot.lowPrice()).isEqualByComparingTo(new BigDecimal("69200"));
        assertThat(snapshot.volume()).isEqualByComparingTo(new BigDecimal("12345678"));
        assertThat(snapshot.snapshotAt()).isEqualTo(fixedInstant.atOffset(KST));

        RequestCapture cap = captured.get();
        assertThat(cap).isNotNull();
        assertThat(cap.headers.get("authorization")).isEqualTo("Bearer tok-1");
        assertThat(cap.headers.get("appkey")).isEqualTo(APP_KEY);
        assertThat(cap.headers.get("appsecret")).isEqualTo(APP_SECRET);
        assertThat(cap.headers.get("tr_id")).isEqualTo("FHKST01010100");
        assertThat(cap.headers.get("custtype")).isEqualTo("P");
        assertThat(cap.query).contains("fid_cond_mrkt_div_code=J");
        assertThat(cap.query).contains("fid_input_iscd=005930");
        assertThat(tokens.invalidateCount.get()).isEqualTo(0);
    }

    @Test
    void fetchPriceRotatesTokenOnAuthError() {
        AtomicInteger calls = new AtomicInteger();
        String authError = """
                {"rt_cd":"1","msg_cd":"EGW00121","msg1":"토큰이 만료되었습니다.","output":{}}
                """;
        String happy = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK","output":{
                  "stck_prpr":"70000","stck_oprc":"0","stck_hgpr":"0","stck_lwpr":"0","acml_vol":"0"}}
                """;
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-price", exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                respondJson(exchange, 200, authError, "");
            } else {
                respondJson(exchange, 200, happy, "");
            }
        });

        CountingTokenService tokens = new CountingTokenService("rotated-token");
        KisMarketDataAdapter adapter = newAdapter(tokens);

        PriceSnapshot snapshot = adapter.fetchPrice(SYMBOL);

        assertThat(snapshot.lastPrice()).isEqualByComparingTo(new BigDecimal("70000"));
        assertThat(calls.get()).isEqualTo(2);
        assertThat(tokens.invalidateCount.get()).isEqualTo(1);
    }

    @Test
    void fetchPricePersistentAuthErrorThrows() {
        String authError = """
                {"rt_cd":"1","msg_cd":"EGW00121","msg1":"토큰이 만료되었습니다.","output":{}}
                """;
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-price",
                exchange -> respondJson(exchange, 200, authError, ""));

        CountingTokenService tokens = new CountingTokenService("tok-stuck");
        KisMarketDataAdapter adapter = newAdapter(tokens);

        assertThatThrownBy(() -> adapter.fetchPrice(SYMBOL))
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.AUTH_FAILED));
        assertThat(tokens.invalidateCount.get()).isEqualTo(1);
    }

    @Test
    void fetchPriceTransientKisErrorThrows() {
        String body = """
                {"rt_cd":"1","msg_cd":"MCA00001","msg1":"기타 오류","output":{}}
                """;
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-price",
                exchange -> respondJson(exchange, 200, body, ""));

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        assertThatThrownBy(() -> adapter.fetchPrice(SYMBOL))
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.TRANSIENT));
    }

    @Test
    void fetchPriceInvalidResponseWhenOutputMissing() {
        String body = "{\"rt_cd\":\"0\",\"msg_cd\":\"MCA00000\",\"msg1\":\"OK\"}";
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-price",
                exchange -> respondJson(exchange, 200, body, ""));

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        assertThatThrownBy(() -> adapter.fetchPrice(SYMBOL))
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.INVALID_RESPONSE));
    }

    @Test
    void fetchPriceEmptyOutputThrowsEmptyResponse() {
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK","output":{
                  "stck_prpr":"","stck_oprc":"","stck_hgpr":"","stck_lwpr":"","acml_vol":""}}
                """;
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-price",
                exchange -> respondJson(exchange, 200, body, ""));

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        assertThatThrownBy(() -> adapter.fetchPrice(SYMBOL))
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.EMPTY_RESPONSE));
    }

    @Test
    void fetchPriceTimeoutThrowsTimeoutCategory() {
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-price", exchange -> {
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
        KisMarketDataAdapter adapter = newAdapter(props, new CountingTokenService("tok-x"));

        Instant start = Instant.now();
        assertThatThrownBy(() -> adapter.fetchPrice(SYMBOL))
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.TIMEOUT));
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void fetchPriceBlankSymbolThrowsRequestInvalidNoHttp() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-price", exchange -> {
            calls.incrementAndGet();
            respondJson(exchange, 200, "{}", "");
        });

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        assertThatThrownBy(() -> adapter.fetchPrice("  "))
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
        assertThat(calls.get()).isEqualTo(0);
    }

    // -----------------------------------------------------------------
    // fetchMinuteBars
    // -----------------------------------------------------------------

    @Test
    void fetchMinuteBarsSinglePageReturnsChronological() {
        String body = """
                {
                  "rt_cd": "0",
                  "msg_cd": "MCA00000",
                  "msg1": "OK",
                  "output2": [
                    {"stck_bsop_date":"20260512","stck_cntg_hour":"100200","stck_oprc":"100","stck_hgpr":"110","stck_lwpr":"95","stck_prpr":"105","cntg_vol":"30"},
                    {"stck_bsop_date":"20260512","stck_cntg_hour":"100100","stck_oprc":"99","stck_hgpr":"101","stck_lwpr":"98","stck_prpr":"100","cntg_vol":"20"},
                    {"stck_bsop_date":"20260512","stck_cntg_hour":"100000","stck_oprc":"100","stck_hgpr":"100","stck_lwpr":"99","stck_prpr":"99","cntg_vol":"10"}
                  ],
                  "ctx_area_fk200": "",
                  "ctx_area_nk200": ""
                }
                """;
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice",
                exchange -> respondJson(exchange, 200, body, ""));

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        List<MinuteBar> bars = adapter.fetchMinuteBars(SYMBOL, LocalDate.of(2026, 5, 12));

        assertThat(bars).hasSize(3);
        // chronological → first should be 10:00:00
        assertThat(bars.get(0).barTime())
                .isEqualTo(OffsetDateTime.of(2026, 5, 12, 10, 0, 0, 0, KST));
        assertThat(bars.get(0).closePrice()).isEqualByComparingTo(new BigDecimal("99"));
        assertThat(bars.get(1).barTime())
                .isEqualTo(OffsetDateTime.of(2026, 5, 12, 10, 1, 0, 0, KST));
        assertThat(bars.get(2).barTime())
                .isEqualTo(OffsetDateTime.of(2026, 5, 12, 10, 2, 0, 0, KST));
        assertThat(bars.get(2).volume()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(bars).allSatisfy(b -> {
            assertThat(b.symbolCode()).isEqualTo(SYMBOL);
            assertThat(b.sourceName()).isEqualTo("kis");
        });
    }

    @Test
    void fetchMinuteBarsPaginatedCombinesPages() {
        AtomicInteger calls = new AtomicInteger();
        String page1 = """
                {
                  "rt_cd": "0", "msg_cd":"MCA00000", "msg1":"OK",
                  "output2": [
                    {"stck_bsop_date":"20260512","stck_cntg_hour":"100200","stck_oprc":"100","stck_hgpr":"110","stck_lwpr":"95","stck_prpr":"105","cntg_vol":"30"},
                    {"stck_bsop_date":"20260512","stck_cntg_hour":"100100","stck_oprc":"99","stck_hgpr":"101","stck_lwpr":"98","stck_prpr":"100","cntg_vol":"20"}
                  ],
                  "ctx_area_fk200": "FK1", "ctx_area_nk200": "NK1"
                }
                """;
        String page2 = """
                {
                  "rt_cd": "0", "msg_cd":"MCA00000", "msg1":"OK",
                  "output2": [
                    {"stck_bsop_date":"20260512","stck_cntg_hour":"100000","stck_oprc":"100","stck_hgpr":"100","stck_lwpr":"99","stck_prpr":"99","cntg_vol":"10"}
                  ],
                  "ctx_area_fk200": "", "ctx_area_nk200": ""
                }
                """;
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice", exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                respondJson(exchange, 200, page1, "F");
            } else {
                respondJson(exchange, 200, page2, "D");
            }
        });

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        List<MinuteBar> bars = adapter.fetchMinuteBars(SYMBOL, LocalDate.of(2026, 5, 12));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(bars).hasSize(3);
        assertThat(bars.get(0).barTime())
                .isEqualTo(OffsetDateTime.of(2026, 5, 12, 10, 0, 0, 0, KST));
        assertThat(bars.get(2).barTime())
                .isEqualTo(OffsetDateTime.of(2026, 5, 12, 10, 2, 0, 0, KST));
    }

    @Test
    void fetchMinuteBarsWrongDayThrowsRequestInvalid() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice", exchange -> {
            calls.incrementAndGet();
            respondJson(exchange, 200, "{}", "");
        });

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        assertThatThrownBy(() -> adapter.fetchMinuteBars(SYMBOL, LocalDate.of(2026, 5, 11)))
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
        assertThat(calls.get()).isEqualTo(0);
    }

    @Test
    void fetchMinuteBarsEmptyOutputReturnsEmptyList() {
        String body = """
                {"rt_cd":"0","msg_cd":"MCA00000","msg1":"OK","output2":[],
                 "ctx_area_fk200":"","ctx_area_nk200":""}
                """;
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice",
                exchange -> respondJson(exchange, 200, body, ""));

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-x"));

        List<MinuteBar> bars = adapter.fetchMinuteBars(SYMBOL, LocalDate.of(2026, 5, 12));

        assertThat(bars).isEmpty();
    }

    // -----------------------------------------------------------------
    // fetchOrderBook
    // -----------------------------------------------------------------

    @Test
    void fetchOrderBookHappyReturnsSnapshotWithParsedTime() {
        String body = """
                {
                  "rt_cd": "0", "msg_cd":"MCA00000", "msg1":"OK",
                  "output1": {"some":"summary"},
                  "output2": {
                    "askp1":"70100","askp2":"70200","askp3":"70300","askp4":"70400","askp5":"70500",
                    "askp6":"70600","askp7":"70700","askp8":"70800","askp9":"70900","askp10":"71000",
                    "bidp1":"70000","bidp2":"69900","bidp3":"69800","bidp4":"69700","bidp5":"69600",
                    "bidp6":"69500","bidp7":"69400","bidp8":"69300","bidp9":"69200","bidp10":"69100",
                    "askp_rsqn1":"10","bidp_rsqn1":"20",
                    "total_askp_rsqn":"1000","total_bidp_rsqn":"2000",
                    "aspr_acpt_hour":"153005"
                  }
                }
                """;
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn",
                exchange -> respondJson(exchange, 200, body, ""));

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-ob"));

        OrderBookSnapshot snap = adapter.fetchOrderBook(SYMBOL);

        assertThat(snap.symbolCode()).isEqualTo(SYMBOL);
        assertThat(snap.sourceName()).isEqualTo("kis");
        assertThat(snap.capturedAt())
                .isEqualTo(OffsetDateTime.of(2026, 5, 12, 15, 30, 5, 0, KST));
        assertThat(snap.payload().get("askp1").asText()).isEqualTo("70100");
        assertThat(snap.payload().get("bidp1").asText()).isEqualTo("70000");
        assertThat(snap.payload().get("total_askp_rsqn").asText()).isEqualTo("1000");
        assertThat(snap.payload().get("total_bidp_rsqn").asText()).isEqualTo("2000");
    }

    @Test
    void fetchOrderBookFallsBackToClockWhenAsprHourMissing() {
        String body = """
                {
                  "rt_cd": "0", "msg_cd":"MCA00000", "msg1":"OK",
                  "output1": {},
                  "output2": {
                    "askp1":"70100","bidp1":"70000",
                    "total_askp_rsqn":"100","total_bidp_rsqn":"200"
                  }
                }
                """;
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn",
                exchange -> respondJson(exchange, 200, body, ""));

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-ob"));

        OrderBookSnapshot snap = adapter.fetchOrderBook(SYMBOL);

        assertThat(snap.capturedAt()).isEqualTo(fixedInstant.atOffset(KST));
    }

    @Test
    void fetchOrderBookBlankSymbolThrowsRequestInvalid() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn", exchange -> {
            calls.incrementAndGet();
            respondJson(exchange, 200, "{}", "");
        });

        KisMarketDataAdapter adapter = newAdapter(new CountingTokenService("tok-ob"));

        assertThatThrownBy(() -> adapter.fetchOrderBook(""))
                .isInstanceOf(MarketDataException.class)
                .satisfies(ex -> assertThat(((MarketDataException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
        assertThat(calls.get()).isEqualTo(0);
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private KisMarketDataAdapter newAdapter(CountingTokenService tokens) {
        return newAdapter(baseProperties(), tokens);
    }

    private KisMarketDataAdapter newAdapter(KisProperties props, CountingTokenService tokens) {
        Duration timeout = Duration.ofSeconds(Math.max(1, props.getRestTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        return new KisMarketDataAdapter(props, tokens, restClient, new ObjectMapper(), clock);
    }

    private KisProperties baseProperties() {
        KisProperties p = new KisProperties();
        p.setAppKey(APP_KEY);
        p.setAppSecret(APP_SECRET);
        p.setBaseUrl("http://127.0.0.1:" + port);
        p.setRestTimeoutSeconds(5);
        return p;
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

    private static com.sun.net.httpserver.HttpHandler recorder(AtomicReference<RequestCapture> captured,
                                                                int status,
                                                                String body,
                                                                String trCont) {
        return (HttpExchange exchange) -> {
            RequestCapture cap = new RequestCapture();
            for (String name : exchange.getRequestHeaders().keySet()) {
                cap.headers.put(name.toLowerCase(java.util.Locale.ROOT),
                        exchange.getRequestHeaders().getFirst(name));
            }
            cap.query = exchange.getRequestURI().getRawQuery();
            captured.set(cap);
            respondJson(exchange, status, body, trCont);
        };
    }

    private static final class RequestCapture {
        private final java.util.Map<String, String> headers = new java.util.HashMap<>();
        private String query;
    }

    /** Test token service that returns a fixed token and counts invalidate() calls. */
    private static final class CountingTokenService extends KisAccessTokenService {
        private final String token;
        private final AtomicInteger invalidateCount = new AtomicInteger();

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
