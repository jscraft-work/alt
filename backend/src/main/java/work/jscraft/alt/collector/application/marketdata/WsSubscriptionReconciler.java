package work.jscraft.alt.collector.application.marketdata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.common.config.ApplicationProfiles;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketClient;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;

/**
 * State-based reconciler — collector-worker 가 매 분 active strategy_instance 의 watchlist 종목을
 * 모아 {@link KisWebSocketClient} 의 구독 상태와 동기화한다.
 *
 * <p>운영자는 admin UI 에서 인스턴스 lifecycle 토글 + watchlist 편집만 하면 되고, 별도 subscribe
 * endpoint 호출 없이 다음 reconcile cycle (최대 60 초 내) 에 자동 반영된다.
 *
 * <p>KIS WS 동시 구독 한도 (≈ 41) 초과 시 신규 종목 일부만 등록 + {@link MarketDataOpsEventRecorder}
 * 의 limit_exceeded 이벤트로 운영자가 ops_event 에서 모니터링.
 */
@Component
@Profile(ApplicationProfiles.COLLECTOR_WORKER)
public class WsSubscriptionReconciler {

    public static final int MAX_SUBSCRIPTIONS = 41;
    public static final String EVENT_TYPE_RECONCILE = "ws.subscription.reconcile";
    public static final String EVENT_TYPE_LIMIT_EXCEEDED = "ws.subscription.limit_exceeded";
    private static final String LIFECYCLE_ACTIVE = "active";
    private static final String STATUS_LIMIT_EXCEEDED = "limit_exceeded";

    private static final Logger log = LoggerFactory.getLogger(WsSubscriptionReconciler.class);

    private final StrategyInstanceRepository strategyInstanceRepository;
    private final StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;
    private final KisWebSocketClient kisWebSocketClient;
    private final MarketDataOpsEventRecorder opsEventRecorder;
    private final WsAdhocSubscriptionStore adhocSubscriptionStore;
    private final WebSocketStatusTracker statusTracker;

    public WsSubscriptionReconciler(
            StrategyInstanceRepository strategyInstanceRepository,
            StrategyInstanceWatchlistRelationRepository watchlistRelationRepository,
            KisWebSocketClient kisWebSocketClient,
            MarketDataOpsEventRecorder opsEventRecorder,
            WsAdhocSubscriptionStore adhocSubscriptionStore,
            WebSocketStatusTracker statusTracker) {
        this.strategyInstanceRepository = strategyInstanceRepository;
        this.watchlistRelationRepository = watchlistRelationRepository;
        this.kisWebSocketClient = kisWebSocketClient;
        this.opsEventRecorder = opsEventRecorder;
        this.adhocSubscriptionStore = adhocSubscriptionStore;
        this.statusTracker = statusTracker;
    }

    @Scheduled(
            initialDelayString = "${app.ws.reconcile.initial-delay-ms:30000}",
            fixedDelayString = "${app.ws.reconcile.interval-ms:60000}")
    @Transactional(readOnly = true)
    public void reconcile() {
        Set<String> desired = collectDesiredSymbols();
        Set<String> current = new HashSet<>(kisWebSocketClient.subscribedCodes());

        Set<String> toUnsubscribe = new HashSet<>(current);
        toUnsubscribe.removeAll(desired);

        Set<String> toSubscribe = new HashSet<>(desired);
        toSubscribe.removeAll(current);

        if (!toUnsubscribe.isEmpty()) {
            kisWebSocketClient.unsubscribe(toUnsubscribe);
            log.info("ws.subscription.unsubscribed count={} sample={}",
                    toUnsubscribe.size(), sample(toUnsubscribe));
        }

        if (!toSubscribe.isEmpty()) {
            int remainingAfterUnsubscribe = current.size() - toUnsubscribe.size();
            int availableSlots = Math.max(0, MAX_SUBSCRIPTIONS - remainingAfterUnsubscribe);
            if (toSubscribe.size() > availableSlots) {
                List<String> accepted = new ArrayList<>(toSubscribe).subList(0, availableSlots);
                List<String> rejected = new ArrayList<>(toSubscribe).subList(availableSlots, toSubscribe.size());
                if (!accepted.isEmpty()) {
                    kisWebSocketClient.subscribe(accepted);
                }
                String message = "KIS WS 동시 구독 한도 초과: requested=" + toSubscribe.size()
                        + " available=" + availableSlots + " rejected=" + rejected.size();
                log.warn("ws.subscription.limit_exceeded {}", message);
                opsEventRecorder.recordFailure(
                        MarketDataOpsEventRecorder.SERVICE_MARKETDATA,
                        EVENT_TYPE_LIMIT_EXCEEDED,
                        sample(rejected),
                        message,
                        STATUS_LIMIT_EXCEEDED);
                return;
            }

            kisWebSocketClient.subscribe(toSubscribe);
            log.info("ws.subscription.subscribed count={} sample={}",
                    toSubscribe.size(), sample(toSubscribe));
        }

        opsEventRecorder.recordSuccess(
                MarketDataOpsEventRecorder.SERVICE_MARKETDATA,
                EVENT_TYPE_RECONCILE,
                null);

        // 매 cycle 끝에 web-app 의 admin 대시보드가 읽도록 현재 구독 코드 set 을 Redis 에 publish.
        statusTracker.recordSubscribedCodes(kisWebSocketClient.subscribedCodes());
    }

    private Set<String> collectDesiredSymbols() {
        Set<String> symbols = new HashSet<>();
        List<StrategyInstanceEntity> activeInstances =
                strategyInstanceRepository.findByLifecycleStateAndAutoPausedReasonIsNull(LIFECYCLE_ACTIVE);
        for (StrategyInstanceEntity instance : activeInstances) {
            UUID instanceId = instance.getId();
            List<StrategyInstanceWatchlistRelationEntity> relations =
                    watchlistRelationRepository.findByStrategyInstanceId(instanceId);
            for (StrategyInstanceWatchlistRelationEntity relation : relations) {
                if (relation.getAssetMaster() == null) {
                    continue;
                }
                symbols.add(relation.getAssetMaster().getSymbolCode());
            }
        }
        // 운영자가 admin UI 에서 명시적으로 추가한 ad-hoc 코드 union.
        symbols.addAll(adhocSubscriptionStore.all());
        return symbols;
    }

    private static String sample(Iterable<String> symbols) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String symbol : symbols) {
            if (count++ > 0) {
                sb.append(',');
            }
            sb.append(symbol);
            if (count >= 3) {
                sb.append(",...");
                break;
            }
        }
        return sb.toString();
    }
}
