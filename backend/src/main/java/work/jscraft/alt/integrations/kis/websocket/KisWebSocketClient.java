package work.jscraft.alt.integrations.kis.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.marketdata.OrderBookRedisCache;
import work.jscraft.alt.collector.application.marketdata.WebSocketStatusTracker;
import work.jscraft.alt.common.config.ApplicationProfiles;
import work.jscraft.alt.integrations.kis.KisProperties;
import work.jscraft.alt.ops.application.AlertEvent;
import work.jscraft.alt.ops.application.AlertGateway;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.ControlMessage;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.DecodedFrame;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.EncryptedFrame;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.OrderBookFrames;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.OrderBookLevels;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.TradeFrames;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.TradeTick;

/**
 * KIS realtime WebSocket client using {@link java.net.http.HttpClient#newWebSocketBuilder()}.
 *
 * <p>Connection lifecycle is owned by this client; full E2E WS transport is not covered by tests,
 * coverage instead relies on the isolated decoder plus the {@link #handleIncomingText(String)}
 * dispatch hook.</p>
 */
// KIS는 앱키당 WS 1세션만 허용 — 여러 컨테이너가 같은 appkey로 붙으면 "invalid approval"로 거부된다.
// 실제 구독(WsSubscriptionReconciler)이 도는 collector-worker 하나만 WS를 소유한다.
@Component
@Profile(ApplicationProfiles.COLLECTOR_WORKER)
public class KisWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(KisWebSocketClient.class);
    private static final String VENDOR = "kis-ws";

    private final KisProperties properties;
    private final KisApprovalKeyService approvalKeyService;
    private final KisWebSocketFrameDecoder frameDecoder;
    private final OrderBookRedisCache orderBookRedisCache;
    private final WebSocketStatusTracker statusTracker;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final AlertGateway alertGateway;
    private final Clock clock;

    private final Set<String> subscribedCodes = ConcurrentHashMap.newKeySet();
    private final AtomicReference<WebSocket> activeSocket = new AtomicReference<>();
    private final AtomicReference<HttpClient> httpClientRef = new AtomicReference<>();
    private final AtomicLong currentBackoffMillis = new AtomicLong();
    private final StringBuilder textAssembler = new StringBuilder();

    private volatile ExecutorService loopExecutor;
    private volatile boolean runRequested;
    private volatile boolean connected;

    @Autowired
    public KisWebSocketClient(
            KisProperties properties,
            KisApprovalKeyService approvalKeyService,
            OrderBookRedisCache orderBookRedisCache,
            WebSocketStatusTracker statusTracker,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            AlertGateway alertGateway,
            Clock clock) {
        this(properties, approvalKeyService,
                new KisWebSocketFrameDecoder(objectMapper, clock),
                orderBookRedisCache, statusTracker, eventPublisher, objectMapper,
                alertGateway, clock);
    }

    // Constructor for tests — explicit decoder.
    KisWebSocketClient(
            KisProperties properties,
            KisApprovalKeyService approvalKeyService,
            KisWebSocketFrameDecoder frameDecoder,
            OrderBookRedisCache orderBookRedisCache,
            WebSocketStatusTracker statusTracker,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            AlertGateway alertGateway,
            Clock clock) {
        this.properties = properties;
        this.approvalKeyService = approvalKeyService;
        this.frameDecoder = frameDecoder;
        this.orderBookRedisCache = orderBookRedisCache;
        this.statusTracker = statusTracker;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.alertGateway = alertGateway;
        this.clock = clock;
        this.currentBackoffMillis.set(
                Math.max(100L, properties.getWebsocket().getReconnectInitialBackoffMillis()));
    }

    @PostConstruct
    void onStart() {
        connect();
    }

    @PreDestroy
    void onStop() {
        try {
            disconnect();
        } finally {
            subscribedCodes.clear();
        }
    }

    public synchronized void connect() {
        if (runRequested) {
            return;
        }
        runRequested = true;
        currentBackoffMillis.set(
                Math.max(100L, properties.getWebsocket().getReconnectInitialBackoffMillis()));
        loopExecutor = Executors.newSingleThreadExecutor(namedDaemon("kis-ws-loop"));
        loopExecutor.submit(this::runReconnectLoop);
    }

    public synchronized void disconnect() {
        runRequested = false;
        WebSocket ws = activeSocket.getAndSet(null);
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "client disconnect");
            } catch (RuntimeException ex) {
                log.debug("KIS WS sendClose failed: {}", ex.getClass().getSimpleName());
            }
        }
        ExecutorService exec = loopExecutor;
        loopExecutor = null;
        if (exec != null) {
            exec.shutdownNow();
            try {
                exec.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        HttpClient client = httpClientRef.getAndSet(null);
        if (client != null) {
            // HttpClient has no close() prior to 21+ but is GC-safe; do nothing else.
        }
        connected = false;
    }

    public void subscribe(Collection<String> symbolCodes) {
        if (symbolCodes == null || symbolCodes.isEmpty()) {
            return;
        }
        Set<String> fresh = new LinkedHashSet<>();
        for (String code : symbolCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            String c = code.trim();
            if (subscribedCodes.add(c)) {
                fresh.add(c);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }
        WebSocket ws = activeSocket.get();
        if (ws == null || !connected) {
            return;
        }
        String approvalKey = tryApprovalKey();
        if (approvalKey == null) {
            return;
        }
        for (String code : fresh) {
            sendSubscribe(ws, approvalKey, KisWebSocketFrameDecoder.TR_TRADE, code, true);
            sendSubscribe(ws, approvalKey, KisWebSocketFrameDecoder.TR_ORDERBOOK, code, true);
        }
    }

    public void unsubscribe(Collection<String> symbolCodes) {
        if (symbolCodes == null || symbolCodes.isEmpty()) {
            return;
        }
        Set<String> removed = new LinkedHashSet<>();
        for (String code : symbolCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            String c = code.trim();
            if (subscribedCodes.remove(c)) {
                removed.add(c);
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        WebSocket ws = activeSocket.get();
        if (ws == null || !connected) {
            return;
        }
        String approvalKey = tryApprovalKey();
        if (approvalKey == null) {
            return;
        }
        for (String code : removed) {
            sendSubscribe(ws, approvalKey, KisWebSocketFrameDecoder.TR_TRADE, code, false);
            sendSubscribe(ws, approvalKey, KisWebSocketFrameDecoder.TR_ORDERBOOK, code, false);
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public Set<String> subscribedCodes() {
        return new LinkedHashSet<>(subscribedCodes);
    }

    private void runReconnectLoop() {
        int consecutiveFailures = 0;
        int maxAttempts = properties.getWebsocket().getReconnectMaxAttempts();
        while (runRequested && !Thread.currentThread().isInterrupted()) {
            boolean established = false;
            try {
                established = openOnce();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException ex) {
                log.warn("KIS WS connect attempt failed: {}", ex.getClass().getSimpleName());
            }
            if (!runRequested) {
                break;
            }
            if (established) {
                // 정상 세션을 한 번이라도 맺었으면 실패 카운트 리셋 (드랍 후 재연결은 정상 동작)
                consecutiveFailures = 0;
            } else {
                consecutiveFailures++;
                if (maxAttempts > 0 && consecutiveFailures >= maxAttempts) {
                    log.error("KIS WS 연속 {}회 연결 실패 — 재시도 중단 (수동 재기동 필요)", consecutiveFailures);
                    alertConnectGiveUp(consecutiveFailures);
                    runRequested = false;
                    break;
                }
            }
            sleepBackoff();
            bumpBackoff();
        }
    }

    private void alertConnectGiveUp(int failures) {
        if (alertGateway == null) {
            return;
        }
        String wsUrl = properties.getWebsocket().getWsUrl();
        try {
            alertGateway.dispatch(new AlertEvent(
                    AlertEvent.Severity.CRITICAL,
                    "KIS WebSocket 연결 중단",
                    "연속 " + failures + "회 연결 실패로 재시도를 멈췄습니다. 실시간 호가 수신이 끊겨"
                            + " paper 체결이 전량 차단됩니다. 원인 확인 후 수동 재기동 필요 (ws-url=" + wsUrl + ")",
                    OffsetDateTime.now(clock),
                    Map.of("vendor", VENDOR, "wsUrl", wsUrl,
                            "consecutiveFailures", Integer.toString(failures),
                            "actionRequired", "manual_restart")));
        } catch (RuntimeException ex) {
            log.warn("KIS WS give-up 경보 발송 실패: {}", ex.getClass().getSimpleName());
        }
    }

    /** @return true if a WebSocket session was established this attempt (even if it later dropped). */
    private boolean openOnce() throws InterruptedException {
        String approvalKey;
        try {
            approvalKey = approvalKeyService.getApprovalKey();
        } catch (RuntimeException ex) {
            log.warn("KIS WS approval_key 발급 실패: {}", ex.getClass().getSimpleName());
            return false;
        }

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(
                        Math.max(1, properties.getWebsocket().getConnectTimeoutSeconds())))
                .build();
        httpClientRef.set(http);

        CompletableFuture<WebSocket> future;
        try {
            future = http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(
                            Math.max(1, properties.getWebsocket().getConnectTimeoutSeconds())))
                    .buildAsync(URI.create(properties.getWebsocket().getWsUrl()),
                            new LoopListener());
        } catch (RuntimeException ex) {
            log.warn("KIS WS buildAsync 호출 실패: {}", ex.getClass().getSimpleName());
            return false;
        }

        WebSocket ws;
        try {
            ws = future.get(
                    Math.max(1, properties.getWebsocket().getConnectTimeoutSeconds()) + 1L,
                    TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException ee) {
            log.warn("KIS WS 연결 실패: {}", ee.getCause() == null
                    ? ee.getClass().getSimpleName() : ee.getCause().getClass().getSimpleName());
            statusTracker.recordDisconnected("connect failed");
            return false;
        } catch (java.util.concurrent.TimeoutException te) {
            log.warn("KIS WS 연결 타임아웃");
            statusTracker.recordDisconnected("connect timeout");
            future.cancel(true);
            return false;
        }

        activeSocket.set(ws);
        connected = true;
        statusTracker.recordConnected();
        currentBackoffMillis.set(
                Math.max(100L, properties.getWebsocket().getReconnectInitialBackoffMillis()));

        // resend subscribe for all currently subscribed codes
        Set<String> snapshot = new HashSet<>(subscribedCodes);
        for (String code : snapshot) {
            sendSubscribe(ws, approvalKey, KisWebSocketFrameDecoder.TR_TRADE, code, true);
            sendSubscribe(ws, approvalKey, KisWebSocketFrameDecoder.TR_ORDERBOOK, code, true);
        }

        // Block until close. The Listener clears activeSocket/connected on close.
        while (runRequested && connected) {
            Thread.sleep(200L);
        }
        return true;
    }

    private void sleepBackoff() {
        long delay = currentBackoffMillis.get();
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void bumpBackoff() {
        long max = Math.max(currentBackoffMillis.get(),
                properties.getWebsocket().getReconnectMaxBackoffMillis());
        long next = Math.min(max, currentBackoffMillis.get() * 2L);
        currentBackoffMillis.set(next);
    }

    private String tryApprovalKey() {
        try {
            return approvalKeyService.getApprovalKey();
        } catch (RuntimeException ex) {
            log.warn("KIS WS approval_key 조회 실패: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    private void sendSubscribe(WebSocket ws, String approvalKey, String trId, String code,
                               boolean subscribe) {
        try {
            Map<String, Object> header = Map.of(
                    "approval_key", approvalKey,
                    "custtype", "P",
                    "tr_type", subscribe ? "1" : "2",
                    "content-type", "utf-8");
            Map<String, Object> input = Map.of(
                    "tr_id", trId,
                    "tr_key", code);
            Map<String, Object> body = Map.of("input", input);
            Map<String, Object> msg = Map.of("header", header, "body", body);
            String json = objectMapper.writeValueAsString(msg);
            ws.sendText(json, true);
        } catch (JsonProcessingException ex) {
            log.warn("KIS WS subscribe JSON 직렬화 실패: tr_id={} code={}", trId, code);
        } catch (RuntimeException ex) {
            log.warn("KIS WS subscribe 전송 실패: tr_id={} code={} reason={}",
                    trId, code, ex.getClass().getSimpleName());
        }
    }

    /**
     * Package-private dispatch hook. The {@link WebSocket.Listener#onText} callback delegates
     * to this method; tests feed frames directly.
     */
    void handleIncomingText(String text) {
        if (text == null) {
            return;
        }
        DecodedFrame frame;
        try {
            frame = frameDecoder.decode(text);
        } catch (RuntimeException ex) {
            log.debug("KIS WS decode 실패: {}", ex.getClass().getSimpleName());
            return;
        }
        try {
            switch (frame) {
                case ControlMessage cm -> handleControl(cm);
                case TradeFrames tf -> handleTrades(tf);
                case OrderBookFrames ob -> handleOrderBook(ob);
                case EncryptedFrame ignored ->
                        log.warn("KIS WS encrypted frame received — dropping");
                case KisWebSocketFrameDecoder.Unknown u ->
                        log.debug("KIS WS unknown frame: {}", u.reason());
            }
        } catch (RuntimeException ex) {
            log.warn("KIS WS dispatch 실패: {}", ex.getClass().getSimpleName());
        }
    }

    private void handleControl(ControlMessage cm) {
        JsonNode root = cm.body();
        if (root == null) {
            return;
        }
        JsonNode header = root.path("header");
        String trId = header.path("tr_id").asText("");
        if (KisWebSocketFrameDecoder.TR_PINGPONG.equals(trId)) {
            WebSocket ws = activeSocket.get();
            if (ws != null) {
                try {
                    ws.sendText(root.toString(), true);
                } catch (RuntimeException ex) {
                    log.debug("KIS WS PINGPONG echo 실패: {}", ex.getClass().getSimpleName());
                }
            }
            return;
        }
        JsonNode body = root.path("body");
        String msg1 = body.path("msg1").asText("");
        String rtCd = body.path("rt_cd").asText("");
        if (!msg1.isEmpty()) {
            log.info("KIS WS control tr_id={} rt_cd={} msg={}", trId, rtCd, msg1);
        }
    }

    private void handleTrades(TradeFrames frames) {
        if (frames.ticks().isEmpty()) {
            return;
        }
        for (TradeTick tick : frames.ticks()) {
            try {
                eventPublisher.publishEvent(new RealtimeTradeEvent(
                        tick.symbolCode(), tick.tradeTime(), tick.price(),
                        tick.cumulativeVolume(), tick.buyQty(), tick.sellQty()));
            } catch (RuntimeException ex) {
                log.warn("KIS WS trade publish 실패: {}", ex.getClass().getSimpleName());
            }
        }
        try {
            statusTracker.recordTick();
        } catch (RuntimeException ex) {
            log.debug("KIS WS recordTick(trade) 실패: {}", ex.getClass().getSimpleName());
        }
    }

    private void handleOrderBook(OrderBookFrames frames) {
        if (frames.books().isEmpty()) {
            return;
        }
        for (OrderBookLevels level : frames.books()) {
            try {
                orderBookRedisCache.store(level.snapshot());
            } catch (RuntimeException ex) {
                log.warn("KIS WS orderbook store 실패: {}", ex.getClass().getSimpleName());
            }
        }
        try {
            statusTracker.recordTick();
        } catch (RuntimeException ex) {
            log.debug("KIS WS recordTick(orderbook) 실패: {}", ex.getClass().getSimpleName());
        }
    }

    private static ThreadFactory namedDaemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    private final class LoopListener implements WebSocket.Listener {
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);
            textAssembler.append(data);
            if (!last) {
                return null;
            }
            String full = textAssembler.toString();
            textAssembler.setLength(0);
            try {
                handleIncomingText(full);
            } catch (RuntimeException ex) {
                // Swallow per-frame errors; never let onText terminate the connection.
                log.debug("KIS WS onText 처리 실패: {}", ex.getClass().getSimpleName());
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            activeSocket.compareAndSet(webSocket, null);
            try {
                statusTracker.recordDisconnected(
                        "close status=" + statusCode + " reason=" + (reason == null ? "" : reason));
            } catch (RuntimeException ex) {
                log.debug("KIS WS recordDisconnected(close) 실패: {}", ex.getClass().getSimpleName());
            }
            log.info("KIS WS closed status={} reason={}", statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected = false;
            activeSocket.compareAndSet(webSocket, null);
            try {
                statusTracker.recordDisconnected(
                        "error " + error.getClass().getSimpleName());
            } catch (RuntimeException ex) {
                log.debug("KIS WS recordDisconnected(error) 실패: {}", ex.getClass().getSimpleName());
            }
            log.warn("KIS WS error: {}", error.getClass().getSimpleName());
        }
    }
}
