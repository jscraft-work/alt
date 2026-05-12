package work.jscraft.alt.integrations.yfinance;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import work.jscraft.alt.macro.application.MacroGateway;
import work.jscraft.alt.macro.application.MacroGatewayException;
import work.jscraft.alt.macro.application.MacroGatewayException.Category;

@Component
public class YfinanceMacroAdapter implements MacroGateway {

    static final String VENDOR = "yfinance";

    private static final Logger log = LoggerFactory.getLogger(YfinanceMacroAdapter.class);

    public static final List<TickerMapping> TICKER_MAP = List.of(
            new TickerMapping("^GSPC", "sp500_close", "sp500_change_pct"),
            new TickerMapping("^IXIC", "nasdaq_close", "nasdaq_change_pct"),
            new TickerMapping("^DJI", "dow_close", "dow_change_pct"),
            new TickerMapping("^RUT", "russell2000_close", "russell2000_change_pct"),
            new TickerMapping("^VIX", "vix", "vix_change_pct"),
            new TickerMapping("^IRX", "us_13w_tbill", null),
            new TickerMapping("^FVX", "us_5y_treasury", null),
            new TickerMapping("^TNX", "us_10y_treasury", null),
            new TickerMapping("^TYX", "us_30y_treasury", null),
            new TickerMapping("USDKRW=X", "usd_krw", "usd_krw_change_pct"),
            new TickerMapping("USDCNY=X", "usd_cny", "usd_cny_change_pct"),
            new TickerMapping("USDJPY=X", "usd_jpy", "usd_jpy_change_pct"),
            new TickerMapping("DX-Y.NYB", "dxy", "dxy_change_pct"),
            new TickerMapping("GC=F", "gold", "gold_change_pct"),
            new TickerMapping("CL=F", "wti", "wti_change_pct"),
            new TickerMapping("HG=F", "copper", "copper_change_pct"),
            new TickerMapping("^N225", "nikkei_close", "nikkei_change_pct"),
            new TickerMapping("^HSI", "hang_seng_close", "hang_seng_change_pct"),
            new TickerMapping("000001.SS", "shanghai_close", "shanghai_change_pct"),
            new TickerMapping("^SOX", "sox_close", "sox_change_pct"),
            new TickerMapping("EWY", "ewy_close", "ewy_change_pct"),
            new TickerMapping("138230.KS", "kr_bond_10y_close", "kr_bond_10y_change_pct"));

    public record TickerMapping(String ticker, String closeField, String changePctField) {
    }

    private final YfinanceProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public YfinanceMacroAdapter(YfinanceProperties properties, ObjectMapper objectMapper) {
        this(properties, buildRestClient(properties), objectMapper);
    }

    public YfinanceMacroAdapter(YfinanceProperties properties,
                                RestClient restClient,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildRestClient(YfinanceProperties properties) {
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getPerTickerTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        ClientHttpRequestFactory requestFactory = factory;
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public MacroSnapshot fetchDailyMacro(LocalDate baseDate) {
        if (baseDate == null) {
            throw new MacroGatewayException(Category.REQUEST_INVALID, VENDOR, "baseDate 미지정");
        }

        int parallelism = Math.max(1, properties.getParallelism());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, r -> {
            Thread t = new Thread(r, "yfinance-fetch");
            t.setDaemon(true);
            return t;
        });

        Map<String, TickerResult> results = new ConcurrentHashMap<>();
        try {
            List<Future<TickerResult>> futures = new ArrayList<>(TICKER_MAP.size());
            for (TickerMapping mapping : TICKER_MAP) {
                futures.add(executor.submit(() -> fetchTicker(mapping)));
            }
            for (int i = 0; i < futures.size(); i++) {
                TickerMapping mapping = TICKER_MAP.get(i);
                Future<TickerResult> future = futures.get(i);
                try {
                    TickerResult result = future.get();
                    results.put(mapping.ticker(), result);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    results.put(mapping.ticker(), TickerResult.missing("interrupted"));
                } catch (ExecutionException ex) {
                    // fetchTicker never throws; defensive
                    results.put(mapping.ticker(), TickerResult.missing("execution_error"));
                }
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }

        List<String> missingTickers = new ArrayList<>();
        for (TickerMapping mapping : TICKER_MAP) {
            TickerResult result = results.get(mapping.ticker());
            if (result == null || result.missing()) {
                missingTickers.add(mapping.ticker());
            }
        }

        if (missingTickers.size() == TICKER_MAP.size()) {
            throw new MacroGatewayException(Category.EMPTY_RESPONSE, VENDOR, "all tickers failed");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("snapshot_date", baseDate.toString());

        ObjectNode tickersNode = payload.putObject("tickers");
        ObjectNode rawDataNode = payload.putObject("raw_data");

        for (TickerMapping mapping : TICKER_MAP) {
            TickerResult result = results.get(mapping.ticker());
            if (result == null || result.missing()) {
                continue;
            }
            // tickers map (flat fields)
            if (result.close() != null) {
                tickersNode.put(mapping.closeField(), result.close());
            } else {
                tickersNode.putNull(mapping.closeField());
            }
            if (mapping.changePctField() != null && result.changePct() != null) {
                tickersNode.put(mapping.changePctField(), result.changePct());
            }

            // raw_data per yahoo symbol
            ObjectNode rawTicker = rawDataNode.putObject(mapping.ticker());
            if (result.close() != null) {
                rawTicker.put("close", result.close());
            } else {
                rawTicker.putNull("close");
            }
            if (result.previousClose() != null) {
                rawTicker.put("previous_close", result.previousClose());
            } else {
                rawTicker.putNull("previous_close");
            }
            if (result.changePct() != null) {
                rawTicker.put("change_pct", result.changePct());
            } else {
                rawTicker.putNull("change_pct");
            }
        }

        ArrayNode missingArr = payload.putArray("missing_tickers");
        for (String t : missingTickers) {
            missingArr.add(t);
        }

        return new MacroSnapshot(baseDate, payload);
    }

    private TickerResult fetchTicker(TickerMapping mapping) {
        String url = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .path("/v7/finance/chart/")
                .pathSegment(mapping.ticker())
                .queryParam("range", "5d")
                .queryParam("interval", "1d")
                .build()
                .encode()
                .toUriString();

        ResponseEntity<String> response;
        try {
            response = restClient.get()
                    .uri(url)
                    .header("User-Agent", properties.getUserAgent())
                    .retrieve()
                    .toEntity(String.class);
        } catch (ResourceAccessException ex) {
            String reason = classifyIoReason(ex);
            log.warn("yfinance ticker fetch failed ticker={} reason={}", mapping.ticker(), reason);
            return TickerResult.missing(reason);
        } catch (RestClientResponseException ex) {
            log.warn("yfinance ticker fetch failed ticker={} reason=http_error status={}",
                    mapping.ticker(), ex.getStatusCode().value());
            return TickerResult.missing("http_error");
        } catch (RuntimeException ex) {
            log.warn("yfinance ticker fetch failed ticker={} reason=request_error type={}",
                    mapping.ticker(), ex.getClass().getSimpleName());
            return TickerResult.missing("request_error");
        }

        HttpStatusCode status = response.getStatusCode();
        if (!status.is2xxSuccessful()) {
            log.warn("yfinance ticker fetch failed ticker={} reason=non_2xx status={}",
                    mapping.ticker(), status.value());
            return TickerResult.missing("non_2xx");
        }

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            log.warn("yfinance ticker fetch failed ticker={} reason=empty_body", mapping.ticker());
            return TickerResult.missing("empty_body");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            log.warn("yfinance ticker fetch failed ticker={} reason=json_parse", mapping.ticker());
            return TickerResult.missing("json_parse");
        }

        JsonNode chart = root.get("chart");
        if (chart == null || chart.isNull()) {
            log.warn("yfinance ticker fetch failed ticker={} reason=missing_chart", mapping.ticker());
            return TickerResult.missing("missing_chart");
        }

        JsonNode error = chart.get("error");
        if (error != null && !error.isNull()) {
            String code = "";
            JsonNode codeNode = error.get("code");
            if (codeNode != null && !codeNode.isNull()) {
                code = codeNode.asText();
            }
            log.warn("yfinance ticker fetch failed ticker={} reason=chart_error code={}",
                    mapping.ticker(), code);
            return TickerResult.missing("chart_error");
        }

        JsonNode resultArr = chart.get("result");
        if (resultArr == null || resultArr.isNull() || !resultArr.isArray() || resultArr.isEmpty()) {
            log.warn("yfinance ticker fetch failed ticker={} reason=missing_result", mapping.ticker());
            return TickerResult.missing("missing_result");
        }

        JsonNode first = resultArr.get(0);
        if (first == null || first.isNull()) {
            log.warn("yfinance ticker fetch failed ticker={} reason=missing_result_entry", mapping.ticker());
            return TickerResult.missing("missing_result_entry");
        }

        Double close = null;
        Double prevClose = null;

        JsonNode indicators = first.get("indicators");
        if (indicators != null && !indicators.isNull()) {
            JsonNode quoteArr = indicators.get("quote");
            if (quoteArr != null && quoteArr.isArray() && !quoteArr.isEmpty()) {
                JsonNode quote = quoteArr.get(0);
                if (quote != null && !quote.isNull()) {
                    JsonNode closeArr = quote.get("close");
                    if (closeArr != null && closeArr.isArray()) {
                        List<Double> nonNull = new ArrayList<>();
                        Iterator<JsonNode> it = closeArr.elements();
                        while (it.hasNext()) {
                            JsonNode v = it.next();
                            if (v != null && !v.isNull() && v.isNumber()) {
                                nonNull.add(v.asDouble());
                            }
                        }
                        int n = nonNull.size();
                        if (n >= 1) {
                            close = nonNull.get(n - 1);
                        }
                        if (n >= 2) {
                            prevClose = nonNull.get(n - 2);
                        }
                    }
                }
            }
        }

        JsonNode meta = first.get("meta");
        if (close == null && meta != null && !meta.isNull()) {
            JsonNode mc = meta.get("regularMarketPrice");
            if (mc != null && !mc.isNull() && mc.isNumber()) {
                close = mc.asDouble();
            }
        }
        if (prevClose == null && meta != null && !meta.isNull()) {
            JsonNode mp = meta.get("previousClose");
            if (mp != null && !mp.isNull() && mp.isNumber()) {
                prevClose = mp.asDouble();
            }
        }

        if (close == null) {
            log.warn("yfinance ticker fetch failed ticker={} reason=no_close", mapping.ticker());
            return TickerResult.missing("no_close");
        }

        Double changePct = null;
        if (mapping.changePctField() != null && prevClose != null && prevClose != 0.0d) {
            changePct = (close - prevClose) / prevClose * 100.0d;
        }

        return new TickerResult(false, close, prevClose, changePct);
    }

    private static String classifyIoReason(ResourceAccessException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return "timeout";
            }
            cause = cause.getCause();
        }
        return "network_error";
    }

    private record TickerResult(boolean missing, Double close, Double previousClose, Double changePct) {
        static TickerResult missing(String reason) {
            return new TickerResult(true, null, null, null);
        }
    }
}
