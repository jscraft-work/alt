package work.jscraft.alt.collector.application.marketdata;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 운영자가 admin UI 에서 명시적으로 추가한 ad-hoc KIS WS 구독 종목 코드 저장소.
 *
 * <p>{@link WsSubscriptionReconciler} 가 active 인스턴스 watchlist 와 함께 reconcile 대상에 포함시킨다.
 * "보조" 용도 — 운영자가 watchlist 외의 종목을 잠깐 모니터링하고 싶을 때 사용. Redis SET 으로 저장해
 * web-app pod (admin endpoint) 와 collector-worker pod (reconciler) 사이를 동기화한다.
 */
@Component
public class WsAdhocSubscriptionStore {

    public static final String KEY = "kis:ws:adhoc_codes";

    private final StringRedisTemplate redisTemplate;

    public WsAdhocSubscriptionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * ad-hoc 코드 SET 에 종목들 추가. 빈/null 코드는 무시. 추가된 코드 수 반환.
     */
    public long addAll(Collection<String> symbolCodes) {
        if (symbolCodes == null || symbolCodes.isEmpty()) {
            return 0L;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String code : symbolCodes) {
            if (code == null) {
                continue;
            }
            String c = code.trim();
            if (!c.isEmpty()) {
                normalized.add(c);
            }
        }
        if (normalized.isEmpty()) {
            return 0L;
        }
        Long added = redisTemplate.opsForSet().add(KEY, normalized.toArray(new String[0]));
        return added == null ? 0L : added;
    }

    /**
     * ad-hoc 코드 SET 에서 단일 종목 제거. 실제 삭제됐는지 여부 반환.
     */
    public boolean remove(String symbolCode) {
        if (symbolCode == null) {
            return false;
        }
        String c = symbolCode.trim();
        if (c.isEmpty()) {
            return false;
        }
        Long removed = redisTemplate.opsForSet().remove(KEY, c);
        return removed != null && removed > 0L;
    }

    /**
     * ad-hoc 코드 SET 의 전체 종목 목록 (정렬된 순서). 빈 경우 빈 Set.
     */
    public Set<String> all() {
        Set<String> members = redisTemplate.opsForSet().members(KEY);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        return new TreeSet<>(members);
    }
}
