package work.jscraft.alt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

import work.jscraft.alt.integrations.yfinance.YfinanceMacroAdapter;
import work.jscraft.alt.integrations.yfinance.YfinanceProperties;
import work.jscraft.alt.macro.application.MacroGateway.MacroSnapshot;
import work.jscraft.alt.macro.application.MacroGatewayException;
import work.jscraft.alt.macro.application.MacroGatewayException.Category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class YfinanceMacroAdapterTest {

    private static final String CHART_PATH_PREFIX = "/v7/finance/chart/";

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Multi-threaded executor so a slow handler does not block others (parallel fetches).
        server.setExecutor(Executors.newFixedThreadPool(16, r -> {
            Thread t = new Thread(r, "yfinance-test-http");
            t.setDaemon(true);
            return t;
        }));
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void happyPathWithThreeTickersMapsFieldsCorrectly() {
        // Provide JSON for ^GSPC, ^IRX, USDKRW=X; others should be in missing_tickers.
        Map<String, String> bodies = new HashMap<>();
        bodies.put("^GSPC", chartBody(new Double[]{5700.0, 5710.0, 5750.0, 5770.0, 5800.0}, 5800.0, 5770.0));
        bodies.put("^IRX", chartBody(new Double[]{4.0, 4.01, 4.02, 4.03, 4.05}, 4.05, 4.03));
        bodies.put("USDKRW=X", chartBody(new Double[]{1380.0, 1382.0, 1385.0, 1386.0, 1390.0}, 1390.0, 1386.0));
        server.createContext("/", dispatchHandler(bodies, Set.of()));

        YfinanceMacroAdapter adapter = newAdapter(properties(baseUrl(), 5, 6));

        MacroSnapshot snapshot = adapter.fetchDailyMacro(LocalDate.of(2026, 5, 12));

        assertThat(snapshot.baseDate()).isEqualTo(LocalDate.of(2026, 5, 12));
        JsonNode payload = snapshot.payload();
        assertThat(payload.get("snapshot_date").asText()).isEqualTo("2026-05-12");

        JsonNode tickers = payload.get("tickers");
        assertThat(tickers.get("sp500_close").asDouble()).isEqualTo(5800.0);
        assertThat(tickers.get("sp500_change_pct").asDouble())
                .isCloseTo((5800.0 - 5770.0) / 5770.0 * 100.0, offset(0.0001));
        assertThat(tickers.get("us_13w_tbill").asDouble()).isEqualTo(4.05);
        // ^IRX has no change_pct mapping → no us_13w_tbill_change_pct
        assertThat(tickers.has("us_13w_tbill_change_pct")).isFalse();
        assertThat(tickers.get("usd_krw").asDouble()).isEqualTo(1390.0);
        assertThat(tickers.get("usd_krw_change_pct").asDouble())
                .isCloseTo((1390.0 - 1386.0) / 1386.0 * 100.0, offset(0.0001));

        JsonNode rawData = payload.get("raw_data");
        assertThat(rawData.has("^GSPC")).isTrue();
        assertThat(rawData.get("^GSPC").get("close").asDouble()).isEqualTo(5800.0);
        assertThat(rawData.get("^GSPC").get("previous_close").asDouble()).isEqualTo(5770.0);
        assertThat(rawData.get("^GSPC").get("change_pct").asDouble())
                .isCloseTo((5800.0 - 5770.0) / 5770.0 * 100.0, offset(0.0001));
        assertThat(rawData.has("^IRX")).isTrue();
        assertThat(rawData.has("USDKRW=X")).isTrue();

        JsonNode missing = payload.get("missing_tickers");
        assertThat(missing.isArray()).isTrue();
        // 23 total ticker entries - 3 returned = 20 missing
        assertThat(missing.size()).isEqualTo(YfinanceMacroAdapter.TICKER_MAP.size() - 3);
    }

    @Test
    void allTickersOkProducesEmptyMissing() {
        // Generic handler returning a 5-day close series for any ticker.
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith(CHART_PATH_PREFIX)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            String body = chartBody(new Double[]{100.0, 101.0, 102.0, 103.0, 104.0}, 104.0, 103.0);
            writeJson(exchange, 200, body);
        });

        YfinanceMacroAdapter adapter = newAdapter(properties(baseUrl(), 5, 6));

        MacroSnapshot snapshot = adapter.fetchDailyMacro(LocalDate.of(2026, 5, 12));

        JsonNode payload = snapshot.payload();
        assertThat(payload.get("missing_tickers").isArray()).isTrue();
        assertThat(payload.get("missing_tickers").size()).isZero();
        // every ticker close mapped
        JsonNode tickers = payload.get("tickers");
        assertThat(tickers.has("sp500_close")).isTrue();
        assertThat(tickers.has("us_13w_tbill")).isTrue();
        assertThat(tickers.has("kr_bond_10y_close")).isTrue();
        // raw_data has all 22 yahoo tickers
        assertThat(payload.get("raw_data").size()).isEqualTo(YfinanceMacroAdapter.TICKER_MAP.size());
    }

    @Test
    void perTickerYahooErrorMarksThatTickerMissing() {
        Map<String, String> bodies = new HashMap<>();
        bodies.put("^GSPC", "{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"No data\"}}}");
        bodies.put("^IXIC", chartBody(new Double[]{18000.0, 18100.0}, 18100.0, 18000.0));
        bodies.put("^DJI", chartBody(new Double[]{38000.0, 38200.0}, 38200.0, 38000.0));
        server.createContext("/", dispatchHandler(bodies, Set.of()));

        YfinanceMacroAdapter adapter = newAdapter(properties(baseUrl(), 5, 6));

        MacroSnapshot snapshot = adapter.fetchDailyMacro(LocalDate.of(2026, 5, 12));

        JsonNode payload = snapshot.payload();
        JsonNode missing = payload.get("missing_tickers");
        boolean gspcMissing = false;
        for (JsonNode n : missing) {
            if ("^GSPC".equals(n.asText())) {
                gspcMissing = true;
                break;
            }
        }
        assertThat(gspcMissing).isTrue();
        assertThat(payload.get("raw_data").has("^IXIC")).isTrue();
        assertThat(payload.get("raw_data").has("^DJI")).isTrue();
        assertThat(payload.get("raw_data").has("^GSPC")).isFalse();
    }

    @Test
    void perTickerTimeoutMarksOnlyThatTickerMissing() {
        Map<String, String> bodies = new HashMap<>();
        bodies.put("^IXIC", chartBody(new Double[]{18000.0, 18100.0}, 18100.0, 18000.0));
        bodies.put("^DJI", chartBody(new Double[]{38000.0, 38200.0}, 38200.0, 38000.0));
        Set<String> slow = Set.of("^GSPC");

        int timeoutSeconds = 1;
        server.createContext("/", dispatchHandler(bodies, slow));

        YfinanceMacroAdapter adapter = newAdapter(properties(baseUrl(), timeoutSeconds, 6));

        Instant start = Instant.now();
        MacroSnapshot snapshot = adapter.fetchDailyMacro(LocalDate.of(2026, 5, 12));
        Duration elapsed = Duration.between(start, Instant.now());

        // Whole call within timeout + margin (parallel, so dominated by slowest)
        assertThat(elapsed).isLessThan(Duration.ofSeconds(timeoutSeconds + 3));

        JsonNode payload = snapshot.payload();
        JsonNode missing = payload.get("missing_tickers");
        boolean gspcMissing = false;
        for (JsonNode n : missing) {
            if ("^GSPC".equals(n.asText())) {
                gspcMissing = true;
                break;
            }
        }
        assertThat(gspcMissing).isTrue();
        assertThat(payload.get("raw_data").has("^IXIC")).isTrue();
        assertThat(payload.get("raw_data").has("^DJI")).isTrue();
    }

    @Test
    void allTickersFailingThrowsEmptyResponse() {
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        YfinanceMacroAdapter adapter = newAdapter(properties(baseUrl(), 5, 6));

        assertThatThrownBy(() -> adapter.fetchDailyMacro(LocalDate.of(2026, 5, 12)))
                .isInstanceOf(MacroGatewayException.class)
                .satisfies(ex -> assertThat(((MacroGatewayException) ex).getCategory())
                        .isEqualTo(Category.EMPTY_RESPONSE));
    }

    @Test
    void malformedJsonForOneTickerOnlyAffectsThatTicker() {
        Map<String, String> bodies = new HashMap<>();
        bodies.put("^GSPC", "{not valid json");
        bodies.put("^IXIC", chartBody(new Double[]{18000.0, 18100.0}, 18100.0, 18000.0));
        server.createContext("/", dispatchHandler(bodies, Set.of()));

        YfinanceMacroAdapter adapter = newAdapter(properties(baseUrl(), 5, 6));

        MacroSnapshot snapshot = adapter.fetchDailyMacro(LocalDate.of(2026, 5, 12));

        JsonNode payload = snapshot.payload();
        boolean gspcMissing = false;
        for (JsonNode n : payload.get("missing_tickers")) {
            if ("^GSPC".equals(n.asText())) {
                gspcMissing = true;
                break;
            }
        }
        assertThat(gspcMissing).isTrue();
        assertThat(payload.get("raw_data").has("^IXIC")).isTrue();
    }

    @Test
    void nullBaseDateThrowsRequestInvalidWithoutHttpCall() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        YfinanceMacroAdapter adapter = newAdapter(properties(baseUrl(), 5, 6));

        assertThatThrownBy(() -> adapter.fetchDailyMacro(null))
                .isInstanceOf(MacroGatewayException.class)
                .satisfies(ex -> assertThat(((MacroGatewayException) ex).getCategory())
                        .isEqualTo(Category.REQUEST_INVALID));
        assertThat(calls.get()).isZero();
    }

    @Test
    void userAgentHeaderIsSentOnEachRequest() {
        Map<String, String> capturedUserAgents = new ConcurrentHashMap<>();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String ticker = decodeTicker(path);
            String ua = exchange.getRequestHeaders().getFirst("User-Agent");
            if (ua != null && ticker != null) {
                capturedUserAgents.put(ticker, ua);
            }
            String body = chartBody(new Double[]{100.0, 101.0}, 101.0, 100.0);
            writeJson(exchange, 200, body);
        });

        YfinanceProperties props = properties(baseUrl(), 5, 6);
        String customUa = "yfinance-test-agent/1.0";
        props.setUserAgent(customUa);
        YfinanceMacroAdapter adapter = newAdapter(props);

        adapter.fetchDailyMacro(LocalDate.of(2026, 5, 12));

        assertThat(capturedUserAgents).isNotEmpty();
        assertThat(capturedUserAgents.values()).allSatisfy(v -> assertThat(v).isEqualTo(customUa));
    }

    @Test
    void singleCloseValueProducesNoChangePctButCloseIsPresent() {
        Map<String, String> bodies = new HashMap<>();
        // single non-null close in array; no meta previousClose; mapping has change_pct field
        String body = """
                {
                  "chart": {
                    "result": [
                      {
                        "meta": {"regularMarketPrice": 5800.0},
                        "timestamp": [1700000000],
                        "indicators": {
                          "quote": [
                            { "close": [5800.0] }
                          ]
                        }
                      }
                    ],
                    "error": null
                  }
                }
                """;
        bodies.put("^GSPC", body);
        server.createContext("/", dispatchHandler(bodies, Set.of()));

        YfinanceMacroAdapter adapter = newAdapter(properties(baseUrl(), 5, 6));

        MacroSnapshot snapshot = adapter.fetchDailyMacro(LocalDate.of(2026, 5, 12));

        JsonNode payload = snapshot.payload();
        JsonNode tickers = payload.get("tickers");
        assertThat(tickers.get("sp500_close").asDouble()).isEqualTo(5800.0);
        assertThat(tickers.has("sp500_change_pct")).isFalse();

        JsonNode raw = payload.get("raw_data").get("^GSPC");
        assertThat(raw.get("close").asDouble()).isEqualTo(5800.0);
        assertThat(raw.get("previous_close").isNull()).isTrue();
        assertThat(raw.get("change_pct").isNull()).isTrue();
    }

    // ---------- helpers ----------

    private YfinanceMacroAdapter newAdapter(YfinanceProperties properties) {
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getPerTickerTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        return new YfinanceMacroAdapter(properties, restClient, new ObjectMapper());
    }

    private static YfinanceProperties properties(String baseUrl, int perTickerTimeoutSeconds, int parallelism) {
        YfinanceProperties p = new YfinanceProperties();
        p.setBaseUrl(baseUrl);
        p.setPerTickerTimeoutSeconds(perTickerTimeoutSeconds);
        p.setParallelism(parallelism);
        return p;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static String chartBody(Double[] closes, double regularMarketPrice, double previousClose) {
        StringBuilder closeArr = new StringBuilder();
        for (int i = 0; i < closes.length; i++) {
            if (i > 0) {
                closeArr.append(",");
            }
            if (closes[i] == null) {
                closeArr.append("null");
            } else {
                closeArr.append(closes[i]);
            }
        }
        return "{\"chart\":{\"result\":[{"
                + "\"meta\":{\"regularMarketPrice\":" + regularMarketPrice
                + ",\"previousClose\":" + previousClose + "},"
                + "\"timestamp\":[1700000000],"
                + "\"indicators\":{\"quote\":[{\"close\":[" + closeArr + "]}]}"
                + "}],\"error\":null}}";
    }

    private static HttpHandler dispatchHandler(Map<String, String> bodyByTicker, Set<String> slowTickers) {
        return (HttpExchange exchange) -> {
            String path = exchange.getRequestURI().getPath();
            String ticker = decodeTicker(path);
            if (ticker == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            if (slowTickers.contains(ticker)) {
                try {
                    Thread.sleep(5_000L);
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
                return;
            }
            String body = bodyByTicker.get(ticker);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            writeJson(exchange, 200, body);
        };
    }

    private static String decodeTicker(String path) {
        if (path == null || !path.startsWith(CHART_PATH_PREFIX)) {
            return null;
        }
        String raw = path.substring(CHART_PATH_PREFIX.length());
        if (raw.isEmpty()) {
            return null;
        }
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
