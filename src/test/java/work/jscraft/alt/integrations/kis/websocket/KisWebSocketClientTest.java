package work.jscraft.alt.integrations.kis.websocket;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.marketdata.OrderBookRedisCache;
import work.jscraft.alt.collector.application.marketdata.WebSocketStatusTracker;
import work.jscraft.alt.integrations.kis.KisProperties;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.OrderBookSnapshot;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Focused unit tests for {@link KisWebSocketClient} dispatch.
 *
 * <p><b>Full end-to-end WebSocket transport is not covered</b> — there is no test WS server
 * dependency (and the task forbids new Gradle deps). Coverage relies on the isolated decoder
 * tests plus this class, which feeds frames directly via the package-private
 * {@link KisWebSocketClient#handleIncomingText(String)} hook.</p>
 */
class KisWebSocketClientTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-11T02:00:00Z"), KST);

    private ObjectMapper objectMapper;
    private KisWebSocketFrameDecoder decoder;
    private CapturingPublisher publisher;
    private RecordingStatusTracker statusTracker;
    private RecordingOrderBookCache orderBookCache;
    private CountingApprovalKeyService approvalKeyService;
    private KisWebSocketClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        decoder = new KisWebSocketFrameDecoder(objectMapper, FIXED_CLOCK);
        publisher = new CapturingPublisher();
        statusTracker = new RecordingStatusTracker();
        orderBookCache = new RecordingOrderBookCache(objectMapper);
        approvalKeyService = new CountingApprovalKeyService();

        KisProperties props = new KisProperties();
        // WS disabled so @PostConstruct (if it ran) would not connect; we construct
        // the client directly here without Spring lifecycle anyway.
        client = new KisWebSocketClient(
                props,
                approvalKeyService,
                decoder,
                orderBookCache,
                statusTracker,
                publisher,
                objectMapper);
    }

    @Test
    void tradeFrameDispatchPublishesEventsAndRecordsTick() {
        String raw = "0|H0STCNT0|002|" + tradeFields("005930", "093015", 81000, 1500, 700, 800)
                + "^" + tradeFields("000660", "093016", 200000, 200, 50, 60);

        client.handleIncomingText(raw);

        assertThat(publisher.events).hasSize(2);
        RealtimeTradeEvent first = (RealtimeTradeEvent) publisher.events.get(0);
        assertThat(first.symbolCode()).isEqualTo("005930");
        assertThat(first.cumulativeVolume()).isEqualTo(1500L);
        assertThat(first.sellQty()).isEqualTo(700L);
        assertThat(first.buyQty()).isEqualTo(800L);

        RealtimeTradeEvent second = (RealtimeTradeEvent) publisher.events.get(1);
        assertThat(second.symbolCode()).isEqualTo("000660");

        assertThat(statusTracker.tickCount.get()).isEqualTo(1);
        assertThat(orderBookCache.stored).isEmpty();
    }

    @Test
    void orderBookFrameDispatchCallsStoreAndRecordsTick() {
        String raw = "0|H0STASP0|001|" + orderBookFields("005930", "093030");

        client.handleIncomingText(raw);

        assertThat(publisher.events).isEmpty();
        assertThat(orderBookCache.stored).hasSize(1);
        OrderBookSnapshot snap = orderBookCache.stored.get(0);
        assertThat(snap.symbolCode()).isEqualTo("005930");
        assertThat(snap.sourceName()).isEqualTo(KisWebSocketFrameDecoder.SOURCE_NAME);
        assertThat(snap.payload().path("ask_prices").size()).isEqualTo(10);
        assertThat(statusTracker.tickCount.get()).isEqualTo(1);
    }

    @Test
    void pingpongDispatchDoesNotPublishOrRecord() {
        String raw = "{\"header\":{\"tr_id\":\"PINGPONG\"}}";
        client.handleIncomingText(raw);
        assertThat(publisher.events).isEmpty();
        assertThat(statusTracker.tickCount.get()).isZero();
        assertThat(orderBookCache.stored).isEmpty();
    }

    @Test
    void encryptedFrameDispatchIsSwallowed() {
        client.handleIncomingText("1|H0STCNT0|001|whatever");
        assertThat(publisher.events).isEmpty();
        assertThat(statusTracker.tickCount.get()).isZero();
    }

    @Test
    void malformedFrameDispatchDoesNotThrow() {
        client.handleIncomingText("0|H0STCNT0|");
        client.handleIncomingText("not a frame");
        client.handleIncomingText("");
        client.handleIncomingText(null);
        assertThat(publisher.events).isEmpty();
        assertThat(statusTracker.tickCount.get()).isZero();
    }

    @Test
    void subscribeAndUnsubscribeMutateSetWhenNotConnected() {
        assertThat(client.subscribedCodes()).isEmpty();
        client.subscribe(List.of("005930", "000660"));
        assertThat(client.subscribedCodes()).containsExactlyInAnyOrder("005930", "000660");

        // Re-subscribing same codes is idempotent.
        client.subscribe(List.of("005930"));
        assertThat(client.subscribedCodes()).containsExactlyInAnyOrder("005930", "000660");

        client.unsubscribe(List.of("005930"));
        assertThat(client.subscribedCodes()).containsExactly("000660");

        // Not connected: approval key should not be requested by subscribe path.
        assertThat(approvalKeyService.calls.get()).isZero();
    }

    // ------------- helpers -------------

    private static String tradeFields(String code, String hhmmss, long price, long acmlVol,
                                       long seln, long shnu) {
        String[] f = new String[46];
        java.util.Arrays.fill(f, "0");
        f[0] = code;
        f[1] = hhmmss;
        f[2] = String.valueOf(price);
        f[12] = "1";
        f[13] = String.valueOf(acmlVol);
        f[19] = String.valueOf(seln);
        f[20] = String.valueOf(shnu);
        return String.join("^", f);
    }

    private static String orderBookFields(String code, String hhmmss) {
        String[] f = new String[59];
        java.util.Arrays.fill(f, "0");
        f[0] = code;
        f[1] = hhmmss;
        for (int j = 0; j < 10; j++) {
            f[3 + j] = String.valueOf(81000 + j);
            f[13 + j] = String.valueOf(80900 - j);
            f[23 + j] = String.valueOf(100 + j);
            f[33 + j] = String.valueOf(200 + j);
        }
        f[43] = "5000";
        f[44] = "6000";
        return String.join("^", f);
    }

    private static final class CapturingPublisher implements ApplicationEventPublisher {
        final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
            events.add(event);
        }
    }

    private static final class CountingApprovalKeyService extends KisApprovalKeyService {
        final AtomicInteger calls = new AtomicInteger();

        CountingApprovalKeyService() {
            super(new KisProperties(),
                    new StringRedisTemplate(mock(RedisConnectionFactory.class)),
                    new ObjectMapper(),
                    org.springframework.web.client.RestClient.builder().build());
        }

        @Override
        public String getApprovalKey() {
            calls.incrementAndGet();
            return "fake-approval-key";
        }

        @Override
        public void invalidate() {
            // no-op
        }
    }

    private static final class RecordingStatusTracker extends WebSocketStatusTracker {
        final AtomicInteger connected = new AtomicInteger();
        final AtomicInteger tickCount = new AtomicInteger();
        final AtomicInteger disconnected = new AtomicInteger();

        RecordingStatusTracker() {
            super(new StringRedisTemplate(mock(RedisConnectionFactory.class)),
                    new MarketDataOpsEventRecorder(
                            mock(OpsEventRepository.class),
                            new ObjectMapper(),
                            Clock.fixed(Instant.parse("2026-05-11T02:00:00Z"), KST)),
                    Clock.fixed(Instant.parse("2026-05-11T02:00:00Z"), KST));
        }

        @Override
        public void recordConnected() {
            connected.incrementAndGet();
        }

        @Override
        public void recordTick() {
            tickCount.incrementAndGet();
        }

        @Override
        public void recordDisconnected(String reason) {
            disconnected.incrementAndGet();
        }
    }

    private static final class RecordingOrderBookCache extends OrderBookRedisCache {
        final List<OrderBookSnapshot> stored = new ArrayList<>();

        RecordingOrderBookCache(ObjectMapper objectMapper) {
            super(new StringRedisTemplate(mock(RedisConnectionFactory.class)), objectMapper);
        }

        @Override
        public void store(OrderBookSnapshot snapshot) {
            stored.add(snapshot);
        }
    }
}
