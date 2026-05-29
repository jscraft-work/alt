package work.jscraft.alt;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.marketdata.WebSocketStatusTracker;
import work.jscraft.alt.collector.application.marketdata.WsAdhocSubscriptionStore;
import work.jscraft.alt.collector.application.marketdata.WsSubscriptionReconciler;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketClient;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsSubscriptionReconcilerTest {

    private StrategyInstanceRepository instanceRepository;
    private StrategyInstanceWatchlistRelationRepository watchlistRepository;
    private KisWebSocketClient kisWebSocketClient;
    private MarketDataOpsEventRecorder opsEventRecorder;
    private WsAdhocSubscriptionStore adhocStore;
    private WebSocketStatusTracker statusTracker;
    private WsSubscriptionReconciler reconciler;

    @BeforeEach
    void setUp() {
        instanceRepository = mock(StrategyInstanceRepository.class);
        watchlistRepository = mock(StrategyInstanceWatchlistRelationRepository.class);
        kisWebSocketClient = mock(KisWebSocketClient.class);
        opsEventRecorder = mock(MarketDataOpsEventRecorder.class);
        adhocStore = mock(WsAdhocSubscriptionStore.class);
        statusTracker = mock(WebSocketStatusTracker.class);
        // 기본: ad-hoc 코드 없음. 개별 테스트가 필요 시 stub 덮어쓰기.
        when(adhocStore.all()).thenReturn(Set.of());
        reconciler = new WsSubscriptionReconciler(
                instanceRepository, watchlistRepository, kisWebSocketClient, opsEventRecorder,
                adhocStore, statusTracker);
    }

    @Test
    void newSymbols_areSubscribed_andSuccessEventRecorded() throws Exception {
        // given: subscribedCodes empty, 1 active instance with 2 watchlist symbols
        StrategyInstanceEntity instance = activeInstance();
        when(instanceRepository.findByLifecycleStateAndAutoPausedReasonIsNull("active"))
                .thenReturn(List.of(instance));
        when(watchlistRepository.findByStrategyInstanceId(instance.getId()))
                .thenReturn(watchlistOf("005930", "000660"));
        when(kisWebSocketClient.subscribedCodes()).thenReturn(new LinkedHashSet<>());

        // when
        reconciler.reconcile();

        // then: 2 종목 subscribe + unsubscribe 호출 X + success 이벤트
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> subscribeCaptor = ArgumentCaptor.forClass(Set.class);
        verify(kisWebSocketClient).subscribe(subscribeCaptor.capture());
        assertThat(subscribeCaptor.getValue()).containsExactlyInAnyOrder("005930", "000660");
        verify(kisWebSocketClient, never()).unsubscribe(any());
        verify(opsEventRecorder).recordSuccess(
                eq(MarketDataOpsEventRecorder.SERVICE_MARKETDATA),
                eq(WsSubscriptionReconciler.EVENT_TYPE_RECONCILE),
                eq(null));
    }

    @Test
    void exceedingLimit_subscribesUpTo41_andRecordsLimitExceededFailure() throws Exception {
        // given: 0 already subscribed, 45 desired symbols → 41 accept, 4 reject
        StrategyInstanceEntity instance = activeInstance();
        when(instanceRepository.findByLifecycleStateAndAutoPausedReasonIsNull("active"))
                .thenReturn(List.of(instance));
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            codes.add(String.format("%06d", i));
        }
        when(watchlistRepository.findByStrategyInstanceId(instance.getId()))
                .thenReturn(watchlistOf(codes.toArray(new String[0])));
        when(kisWebSocketClient.subscribedCodes()).thenReturn(new LinkedHashSet<>());

        // when
        reconciler.reconcile();

        // then: 41 만 subscribe + failure 이벤트 (limit_exceeded)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> subscribeCaptor = ArgumentCaptor.forClass(List.class);
        verify(kisWebSocketClient).subscribe(subscribeCaptor.capture());
        assertThat(subscribeCaptor.getValue()).hasSize(41);
        verify(opsEventRecorder).recordFailure(
                eq(MarketDataOpsEventRecorder.SERVICE_MARKETDATA),
                eq(WsSubscriptionReconciler.EVENT_TYPE_LIMIT_EXCEEDED),
                any(),
                any(),
                eq("limit_exceeded"));
        // 한도 초과 시에는 success event 안 적재
        verify(opsEventRecorder, never()).recordSuccess(any(), any(), any());
    }

    @Test
    void staleSymbols_areUnsubscribed_whenInstanceDeactivated() {
        // given: subscribedCodes(A,B,C), no active instance → A,B,C 모두 unsubscribe
        when(instanceRepository.findByLifecycleStateAndAutoPausedReasonIsNull("active"))
                .thenReturn(List.of());
        when(kisWebSocketClient.subscribedCodes())
                .thenReturn(new LinkedHashSet<>(List.of("005930", "000660", "035420")));

        // when
        reconciler.reconcile();

        // then: 3 종목 unsubscribe + subscribe 호출 X
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> unsubscribeCaptor = ArgumentCaptor.forClass(Set.class);
        verify(kisWebSocketClient).unsubscribe(unsubscribeCaptor.capture());
        assertThat(unsubscribeCaptor.getValue()).containsExactlyInAnyOrder("005930", "000660", "035420");
        verify(kisWebSocketClient, never()).subscribe(any());
        verify(opsEventRecorder).recordSuccess(
                eq(MarketDataOpsEventRecorder.SERVICE_MARKETDATA),
                eq(WsSubscriptionReconciler.EVENT_TYPE_RECONCILE),
                eq(null));
    }

    private StrategyInstanceEntity activeInstance() throws Exception {
        StrategyInstanceEntity instance = new StrategyInstanceEntity();
        setId(instance, UUID.randomUUID());
        return instance;
    }

    private List<StrategyInstanceWatchlistRelationEntity> watchlistOf(String... symbolCodes) {
        List<StrategyInstanceWatchlistRelationEntity> relations = new ArrayList<>();
        for (String code : symbolCodes) {
            AssetMasterEntity asset = new AssetMasterEntity();
            asset.setSymbolCode(code);
            StrategyInstanceWatchlistRelationEntity relation = new StrategyInstanceWatchlistRelationEntity();
            relation.setAssetMaster(asset);
            relations.add(relation);
        }
        return relations;
    }

    private void setId(Object entity, UUID id) throws Exception {
        Class<?> klass = entity.getClass();
        while (klass != null) {
            try {
                Field f = klass.getDeclaredField("id");
                f.setAccessible(true);
                f.set(entity, id);
                return;
            } catch (NoSuchFieldException ignored) {
                klass = klass.getSuperclass();
            }
        }
        throw new IllegalStateException("No 'id' field found on " + entity.getClass());
    }
}
