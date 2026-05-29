package work.jscraft.alt.ops.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.marketdata.WebSocketStatusTracker;
import work.jscraft.alt.collector.application.marketdata.WebSocketStatusTracker.WebSocketState;
import work.jscraft.alt.collector.application.marketdata.WsAdhocSubscriptionStore;
import work.jscraft.alt.collector.application.marketdata.WsSubscriptionReconciler;
import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemRepository;
import work.jscraft.alt.macro.infrastructure.persistence.MacroItemRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemRepository;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;

/**
 * data-collection admin 화면 (F2) 의 통합 집계 서비스.
 *
 * <p>4 섹션 — KIS WS / KIS REST 시세 / DART+네이버 / yfinance 매크로 — 각각:
 * <ul>
 *   <li>마지막 데이터 수신 시간 (snapshot_at / bar_time / published_at / base_date)</li>
 *   <li>최근 24h 성공/실패 ops_event 카운트</li>
 *   <li>가장 최근 ops_event 1건 (status_code + message + occurredAt)</li>
 *   <li>운영자 판단용 status 종합 (ok / delayed / down / unknown)</li>
 * </ul>
 *
 * <p>WS 섹션은 추가로:
 * <ul>
 *   <li>구독 종목 수 / 41 한도</li>
 *   <li>연결 상태 (WebSocketStatusTracker)</li>
 *   <li>watchlist 자동 구독 + ad-hoc 추가 분리 표시</li>
 * </ul>
 */
@Service
public class CollectionDashboardService {

    private static final int MAX_SUBSCRIPTIONS = WsSubscriptionReconciler.MAX_SUBSCRIPTIONS;

    public static final String SECTION_WS = "marketdata_ws";
    public static final String SECTION_REST = "marketdata_rest";
    public static final String SECTION_CONTENT = "content";
    public static final String SECTION_MACRO = "macro";

    private final OpsEventRepository opsEventRepository;
    private final MarketPriceItemRepository marketPriceItemRepository;
    private final MarketMinuteItemRepository marketMinuteItemRepository;
    private final NewsItemRepository newsItemRepository;
    private final DisclosureItemRepository disclosureItemRepository;
    private final MacroItemRepository macroItemRepository;
    private final WebSocketStatusTracker statusTracker;
    private final WsAdhocSubscriptionStore adhocSubscriptionStore;
    private final Clock clock;

    public CollectionDashboardService(
            OpsEventRepository opsEventRepository,
            MarketPriceItemRepository marketPriceItemRepository,
            MarketMinuteItemRepository marketMinuteItemRepository,
            NewsItemRepository newsItemRepository,
            DisclosureItemRepository disclosureItemRepository,
            MacroItemRepository macroItemRepository,
            WebSocketStatusTracker statusTracker,
            WsAdhocSubscriptionStore adhocSubscriptionStore,
            Clock clock) {
        this.opsEventRepository = opsEventRepository;
        this.marketPriceItemRepository = marketPriceItemRepository;
        this.marketMinuteItemRepository = marketMinuteItemRepository;
        this.newsItemRepository = newsItemRepository;
        this.disclosureItemRepository = disclosureItemRepository;
        this.macroItemRepository = macroItemRepository;
        this.statusTracker = statusTracker;
        this.adhocSubscriptionStore = adhocSubscriptionStore;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CollectionSummary summary() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime since24h = now.minusHours(24);

        // ─── KIS WS 섹션 ─────────────────────────────────
        WebSocketState wsState = statusTracker.currentState();
        Optional<OffsetDateTime> lastTick =
                statusTracker.readTimestamp(WebSocketStatusTracker.KEY_LAST_TICK_AT);
        Optional<OffsetDateTime> lastConnected =
                statusTracker.readTimestamp(WebSocketStatusTracker.KEY_LAST_CONNECTED_AT);
        Optional<OffsetDateTime> lastDisconnected =
                statusTracker.readTimestamp(WebSocketStatusTracker.KEY_LAST_DISCONNECTED_AT);
        int subscribedCount = statusTracker.currentSubscribedCodes().size();
        int adhocCount = adhocSubscriptionStore.all().size();

        long marketdataOk = opsEventRepository.countByServiceAndStatusSince(
                MarketDataOpsEventRecorder.SERVICE_MARKETDATA,
                MarketDataOpsEventRecorder.STATUS_OK,
                since24h);
        long marketdataDown = opsEventRepository.countByServiceAndStatusSince(
                MarketDataOpsEventRecorder.SERVICE_MARKETDATA,
                MarketDataOpsEventRecorder.STATUS_DOWN,
                since24h);
        long marketdataLimitExceeded = opsEventRepository.countByServiceAndStatusSince(
                MarketDataOpsEventRecorder.SERVICE_MARKETDATA,
                "limit_exceeded",
                since24h);
        Optional<OpsEventEntity> marketdataLatestEvent = opsEventRepository
                .findFirstByServiceNameOrderByOccurredAtDesc(
                        MarketDataOpsEventRecorder.SERVICE_MARKETDATA);

        WsSectionView wsSection = new WsSectionView(
                classifyDataAge(wsState.name(), lastTick.orElse(null), now,
                        SECONDS_5MIN, SECONDS_15MIN),
                wsState.name(),
                lastTick.orElse(null),
                lastConnected.orElse(null),
                lastDisconnected.orElse(null),
                subscribedCount,
                MAX_SUBSCRIPTIONS,
                adhocCount,
                marketdataOk,
                marketdataDown,
                marketdataLimitExceeded,
                opsEventView(marketdataLatestEvent));

        // ─── KIS REST 시세 (snapshot + minute bar) ─────
        Optional<OffsetDateTime> lastSnapshot = marketPriceItemRepository.findMaxSnapshotAt();
        Optional<OffsetDateTime> lastBar = marketMinuteItemRepository.findMaxBarTime();
        OffsetDateTime restLastDataAt = newer(lastSnapshot.orElse(null), lastBar.orElse(null));
        // REST 데이터 fail 은 ops_event 의 marketdata service 와 공유 — 별도 분리하지 않음.
        // 분리하려면 별도 event_type prefix 가 필요 (현재 미적용). 운영자 직관: lastDataAt 만으로 충분.
        RestSectionView restSection = new RestSectionView(
                classifyDataAge("data_only", restLastDataAt, now,
                        SECONDS_30MIN, SECONDS_2H),
                lastSnapshot.orElse(null),
                lastBar.orElse(null));

        // ─── DART + 네이버 (news + disclosure) ─────────
        Optional<OffsetDateTime> lastNews = newsItemRepository.findMaxPublishedAt();
        Optional<OffsetDateTime> lastDisclosure = disclosureItemRepository.findMaxPublishedAt();
        long newsOk = opsEventRepository.countByServiceAndStatusSince(
                MarketDataOpsEventRecorder.SERVICE_NEWS,
                MarketDataOpsEventRecorder.STATUS_OK,
                since24h);
        long newsDown = opsEventRepository.countByServiceAndStatusSince(
                MarketDataOpsEventRecorder.SERVICE_NEWS,
                MarketDataOpsEventRecorder.STATUS_DOWN,
                since24h);
        long disclosureOk = opsEventRepository.countByServiceAndStatusSince(
                MarketDataOpsEventRecorder.SERVICE_DISCLOSURE,
                MarketDataOpsEventRecorder.STATUS_OK,
                since24h);
        long disclosureDown = opsEventRepository.countByServiceAndStatusSince(
                MarketDataOpsEventRecorder.SERVICE_DISCLOSURE,
                MarketDataOpsEventRecorder.STATUS_DOWN,
                since24h);
        Optional<OpsEventEntity> contentLatestEvent = newerEvent(
                opsEventRepository.findFirstByServiceNameOrderByOccurredAtDesc(
                        MarketDataOpsEventRecorder.SERVICE_NEWS),
                opsEventRepository.findFirstByServiceNameOrderByOccurredAtDesc(
                        MarketDataOpsEventRecorder.SERVICE_DISCLOSURE));
        OffsetDateTime contentLastDataAt = newer(lastNews.orElse(null), lastDisclosure.orElse(null));
        ContentSectionView contentSection = new ContentSectionView(
                classifyDataAge("data_only", contentLastDataAt, now,
                        SECONDS_2H, SECONDS_12H),
                lastNews.orElse(null),
                lastDisclosure.orElse(null),
                newsOk, newsDown,
                disclosureOk, disclosureDown,
                opsEventView(contentLatestEvent));

        // ─── yfinance 매크로 ───────────────────────────
        Optional<LocalDate> lastMacroDate = macroItemRepository.findMaxBaseDate();
        long macroOk = opsEventRepository.countByServiceAndStatusSince(
                MarketDataOpsEventRecorder.SERVICE_MACRO,
                MarketDataOpsEventRecorder.STATUS_OK,
                since24h);
        long macroDown = opsEventRepository.countByServiceAndStatusSince(
                MarketDataOpsEventRecorder.SERVICE_MACRO,
                MarketDataOpsEventRecorder.STATUS_DOWN,
                since24h);
        Optional<OpsEventEntity> macroLatestEvent = opsEventRepository
                .findFirstByServiceNameOrderByOccurredAtDesc(
                        MarketDataOpsEventRecorder.SERVICE_MACRO);
        // 매크로는 일별 데이터 — 3일 이상 멈춰있으면 지연, 7일 이상이면 down.
        String macroStatus = classifyMacro(lastMacroDate.orElse(null), now);
        MacroSectionView macroSection = new MacroSectionView(
                macroStatus,
                lastMacroDate.orElse(null),
                macroOk, macroDown,
                opsEventView(macroLatestEvent));

        return new CollectionSummary(now, wsSection, restSection, contentSection, macroSection);
    }

    /**
     * KIS WS 구독 종목 목록 — admin UI 의 종목 표 + 추가/제거 UX 의 1차 데이터 출처.
     */
    @Transactional(readOnly = true)
    public SubscriptionListView listSubscriptions() {
        var current = statusTracker.currentSubscribedCodes();
        var adhoc = adhocSubscriptionStore.all();
        WebSocketState wsState = statusTracker.currentState();
        Optional<OffsetDateTime> lastTick =
                statusTracker.readTimestamp(WebSocketStatusTracker.KEY_LAST_TICK_AT);

        List<SubscriptionRow> rows = new ArrayList<>();
        for (String code : current) {
            rows.add(new SubscriptionRow(code, adhoc.contains(code) ? "adhoc" : "watchlist"));
        }
        // ad-hoc 에는 있지만 아직 reconcile 안 된 코드도 보여주기 (next cycle 에 추가 예정)
        for (String code : adhoc) {
            if (!current.contains(code)) {
                rows.add(new SubscriptionRow(code, "adhoc_pending"));
            }
        }
        return new SubscriptionListView(
                rows,
                rows.size(),
                MAX_SUBSCRIPTIONS,
                wsState.name(),
                lastTick.orElse(null));
    }

    @Transactional(readOnly = true)
    public List<OpsEventView> recentOpsEvents(String serviceName, int limit) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("service 파라미터는 필수입니다");
        }
        int effective = Math.max(1, Math.min(limit, 100));
        List<OpsEventEntity> events = opsEventRepository.findByServiceNameOrderByOccurredAtDesc(
                serviceName.trim(), Limit.of(effective));
        List<OpsEventView> result = new ArrayList<>(events.size());
        for (OpsEventEntity e : events) {
            result.add(toView(e));
        }
        return result;
    }

    public long subscribeAdhoc(List<String> symbolCodes) {
        return adhocSubscriptionStore.addAll(symbolCodes);
    }

    public boolean unsubscribeAdhoc(String symbolCode) {
        return adhocSubscriptionStore.remove(symbolCode);
    }

    // ─── helpers ───────────────────────────────────────

    private static final long SECONDS_5MIN = 300L;
    private static final long SECONDS_15MIN = 900L;
    private static final long SECONDS_30MIN = 1_800L;
    private static final long SECONDS_2H = 7_200L;
    private static final long SECONDS_12H = 43_200L;

    private static String classifyDataAge(
            String stateHint,
            OffsetDateTime lastDataAt,
            OffsetDateTime now,
            long delayedSeconds,
            long downSeconds) {
        if (lastDataAt == null) {
            return "unknown";
        }
        long ageSeconds = java.time.Duration.between(lastDataAt, now).getSeconds();
        if (ageSeconds < delayedSeconds) {
            // WS 의 경우 stateHint 가 DISCONNECTED 면 ok 라도 down 처리.
            if ("DISCONNECTED".equals(stateHint)) {
                return "down";
            }
            return "ok";
        }
        if (ageSeconds < downSeconds) {
            return "delayed";
        }
        return "down";
    }

    private static String classifyMacro(LocalDate lastDate, OffsetDateTime now) {
        if (lastDate == null) {
            return "unknown";
        }
        LocalDate today = now.toLocalDate();
        long daysGap = java.time.temporal.ChronoUnit.DAYS.between(lastDate, today);
        if (daysGap <= 2) {
            return "ok";
        }
        if (daysGap <= 7) {
            return "delayed";
        }
        return "down";
    }

    private static OffsetDateTime newer(OffsetDateTime a, OffsetDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private static Optional<OpsEventEntity> newerEvent(
            Optional<OpsEventEntity> a, Optional<OpsEventEntity> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a.get().getOccurredAt().isAfter(b.get().getOccurredAt()) ? a : b;
    }

    private static OpsEventView opsEventView(Optional<OpsEventEntity> event) {
        return event.map(CollectionDashboardService::toView).orElse(null);
    }

    private static OpsEventView toView(OpsEventEntity e) {
        return new OpsEventView(
                e.getId(),
                e.getOccurredAt(),
                e.getServiceName(),
                e.getEventType(),
                e.getStatusCode(),
                e.getMessage(),
                e.getPayloadJson() == null ? null : e.getPayloadJson().toString());
    }

    // ─── view records ──────────────────────────────────

    public record CollectionSummary(
            OffsetDateTime evaluatedAt,
            WsSectionView ws,
            RestSectionView rest,
            ContentSectionView content,
            MacroSectionView macro) {
    }

    public record WsSectionView(
            String status,
            String connectionState,
            OffsetDateTime lastTickAt,
            OffsetDateTime lastConnectedAt,
            OffsetDateTime lastDisconnectedAt,
            int subscribedCount,
            int maxSubscriptions,
            int adhocCount,
            long count24hOk,
            long count24hDown,
            long count24hLimitExceeded,
            OpsEventView latestEvent) {
    }

    public record RestSectionView(
            String status,
            OffsetDateTime lastSnapshotAt,
            OffsetDateTime lastMinuteBarAt) {
    }

    public record ContentSectionView(
            String status,
            OffsetDateTime lastNewsAt,
            OffsetDateTime lastDisclosureAt,
            long newsCount24hOk,
            long newsCount24hDown,
            long disclosureCount24hOk,
            long disclosureCount24hDown,
            OpsEventView latestEvent) {
    }

    public record MacroSectionView(
            String status,
            LocalDate lastBaseDate,
            long count24hOk,
            long count24hDown,
            OpsEventView latestEvent) {
    }

    public record SubscriptionListView(
            List<SubscriptionRow> rows,
            int totalCount,
            int maxSubscriptions,
            String connectionState,
            OffsetDateTime lastTickAt) {
    }

    /**
     * @param source {@code "watchlist"} (auto-reconciler 가 추가한 watchlist 종목),
     *               {@code "adhoc"} (운영자 명시 추가 + 이미 구독 중),
     *               {@code "adhoc_pending"} (운영자 명시 추가 + 다음 reconcile cycle 대기 중)
     */
    public record SubscriptionRow(
            String symbolCode,
            String source) {
    }

    public record OpsEventView(
            java.util.UUID id,
            OffsetDateTime occurredAt,
            String serviceName,
            String eventType,
            String statusCode,
            String message,
            String payloadJson) {
    }
}
