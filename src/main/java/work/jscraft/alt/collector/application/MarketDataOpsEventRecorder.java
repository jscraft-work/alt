package work.jscraft.alt.collector.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;

@Component
public class MarketDataOpsEventRecorder {

    public static final String SERVICE_MARKETDATA = "marketdata";
    public static final String SERVICE_NEWS = "news";
    public static final String SERVICE_DISCLOSURE = "disclosure";
    public static final String SERVICE_MACRO = "macro";
    public static final String STATUS_OK = "ok";
    public static final String STATUS_DOWN = "down";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OpsEventRepository opsEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MarketDataOpsEventRecorder(
            OpsEventRepository opsEventRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.opsEventRepository = opsEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void recordSuccess(String serviceName, String eventType, String symbolCode) {
        OpsEventEntity event = baseEvent(serviceName, eventType, STATUS_OK, null);
        event.setPayloadJson(symbolPayload(symbolCode));
        opsEventRepository.saveAndFlush(event);
    }

    public void recordFailure(
            String serviceName,
            String eventType,
            String symbolCode,
            String message,
            String category) {
        OpsEventEntity event = baseEvent(serviceName, eventType, STATUS_DOWN, message);
        ObjectNode payload = symbolPayload(symbolCode);
        if (category != null) {
            payload.put("category", category);
        }
        event.setPayloadJson(payload);
        opsEventRepository.saveAndFlush(event);
    }

    private OpsEventEntity baseEvent(String serviceName, String eventType, String statusCode, String message) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OpsEventEntity event = new OpsEventEntity();
        event.setOccurredAt(now);
        event.setBusinessDate(now.atZoneSameInstant(KST).toLocalDate());
        event.setEventType(eventType);
        event.setServiceName(serviceName);
        event.setStatusCode(statusCode);
        event.setMessage(message);
        return event;
    }

    private ObjectNode symbolPayload(String symbolCode) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (symbolCode != null) {
            payload.put("symbolCode", symbolCode);
        }
        return payload;
    }
}
