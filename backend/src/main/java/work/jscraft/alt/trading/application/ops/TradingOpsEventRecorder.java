package work.jscraft.alt.trading.application.ops;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;

/**
 * Trading-worker side ops_event writer. Mirrors {@code MarketDataOpsEventRecorder} on the collector
 * side but with {@code serviceName="trading"} and the cases enumerated in docs/05 §15.2.
 */
@Component
public class TradingOpsEventRecorder {

    public static final String SERVICE = "trading";

    public static final String EVENT_LLM_FAILED = "llm_failed";
    public static final String EVENT_DECISION_PARSE_FAILED = "decision_parse_failed";
    public static final String EVENT_RECONCILE_FAILED = "reconcile_failed";
    public static final String EVENT_AUTO_PAUSED = "auto_paused";
    public static final String EVENT_LIVE_ORDER_FAILED = "live_order_failed";

    public static final String STATUS_DOWN = "down";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OpsEventRepository opsEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TradingOpsEventRecorder(
            OpsEventRepository opsEventRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.opsEventRepository = opsEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void recordFailure(
            StrategyInstanceEntity instance,
            String eventType,
            String message,
            Map<String, String> extraTags) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OpsEventEntity event = new OpsEventEntity();
        event.setOccurredAt(now);
        event.setBusinessDate(now.atZoneSameInstant(KST).toLocalDate());
        event.setEventType(eventType);
        event.setServiceName(SERVICE);
        event.setStatusCode(STATUS_DOWN);
        event.setMessage(message);
        event.setStrategyInstance(instance);
        event.setPayloadJson(payload(instance != null ? instance.getId() : null, extraTags));
        opsEventRepository.saveAndFlush(event);
    }

    private ObjectNode payload(UUID instanceId, Map<String, String> extraTags) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (instanceId != null) {
            payload.put("instanceId", instanceId.toString());
        }
        if (extraTags != null) {
            extraTags.forEach((k, v) -> {
                if (v != null) {
                    payload.put(k, v);
                }
            });
        }
        return payload;
    }
}
