package work.jscraft.alt.trading.application.ops;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import work.jscraft.alt.ops.application.AlertEvent;
import work.jscraft.alt.ops.application.AlertEvent.Severity;
import work.jscraft.alt.ops.application.AlertGateway;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.decision.DecisionParseException;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;

/**
 * Single entry point for trading-side incident reporting: writes an ops_event row AND dispatches
 * an operator alert. Each call is best-effort — alert dispatch failures are swallowed so the
 * trading cycle flow is never blocked by alert/ops_event writes.
 */
@Component
public class TradingIncidentReporter {

    private static final Logger log = LoggerFactory.getLogger(TradingIncidentReporter.class);

    private final AlertGateway alertGateway;
    private final TradingOpsEventRecorder opsEventRecorder;
    private final Clock clock;

    public TradingIncidentReporter(
            AlertGateway alertGateway,
            TradingOpsEventRecorder opsEventRecorder,
            Clock clock) {
        this.alertGateway = alertGateway;
        this.opsEventRecorder = opsEventRecorder;
        this.clock = clock;
    }

    public void reportLlmFailure(StrategyInstanceEntity instance, UUID cycleLogId, LlmCallResult result) {
        Map<String, String> tags = baseTags(instance, cycleLogId);
        tags.put("status", result.callStatus().name());
        String message = "LLM_" + result.callStatus().name() + ": " + nullSafe(result.failureMessage());
        opsEventRecorder.recordFailure(
                instance, TradingOpsEventRecorder.EVENT_LLM_FAILED, message, tags);
        dispatch(Severity.WARNING, "LLM 호출 실패", instanceLine(instance) + " | " + message, tags);
    }

    public void reportDecisionParseFailure(
            StrategyInstanceEntity instance, UUID cycleLogId, DecisionParseException ex) {
        Map<String, String> tags = baseTags(instance, cycleLogId);
        tags.put("reason", ex.reason().name());
        String message = "DECISION_" + ex.reason().name() + ": " + nullSafe(ex.getMessage());
        opsEventRecorder.recordFailure(
                instance, TradingOpsEventRecorder.EVENT_DECISION_PARSE_FAILED, message, tags);
        dispatch(Severity.WARNING, "판단 파싱 실패", instanceLine(instance) + " | " + message, tags);
    }

    public void reportReconcileFailure(StrategyInstanceEntity instance, String failureMessage) {
        Map<String, String> tags = baseTags(instance, null);
        opsEventRecorder.recordFailure(
                instance, TradingOpsEventRecorder.EVENT_RECONCILE_FAILED, failureMessage, tags);
        dispatch(Severity.WARNING, "Reconcile 실패",
                instanceLine(instance) + " | " + nullSafe(failureMessage), tags);
    }

    public void reportAutoPaused(StrategyInstanceEntity instance, String reason) {
        Map<String, String> tags = baseTags(instance, null);
        tags.put("reason", reason);
        String message = "auto_paused reason=" + reason;
        opsEventRecorder.recordFailure(
                instance, TradingOpsEventRecorder.EVENT_AUTO_PAUSED, message, tags);
        dispatch(Severity.CRITICAL, "자동 일시중지",
                instanceLine(instance) + " | " + message, tags);
    }

    public void reportLiveOrderFailure(
            StrategyInstanceEntity instance,
            TradeOrderIntentEntity intent,
            String clientOrderId,
            String failureReason) {
        Map<String, String> tags = baseTags(instance, null);
        if (intent != null) {
            tags.put("intentId", intent.getId() != null ? intent.getId().toString() : "");
            tags.put("symbol", nullSafe(intent.getSymbolCode()));
            tags.put("side", nullSafe(intent.getSide()));
        }
        if (clientOrderId != null) {
            tags.put("clientOrderId", clientOrderId);
        }
        String message = "live order failed: " + nullSafe(failureReason);
        opsEventRecorder.recordFailure(
                instance, TradingOpsEventRecorder.EVENT_LIVE_ORDER_FAILED, message, tags);
        dispatch(Severity.WARNING, "주문 실패",
                instanceLine(instance) + " | " + message, tags);
    }

    private void dispatch(Severity severity, String title, String message, Map<String, String> tags) {
        try {
            alertGateway.dispatch(new AlertEvent(
                    severity, title, message, OffsetDateTime.now(clock), Map.copyOf(tags)));
        } catch (RuntimeException ex) {
            log.warn("alert dispatch swallowed title={} cause={}/{}", title,
                    ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    private Map<String, String> baseTags(StrategyInstanceEntity instance, UUID cycleLogId) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (instance != null && instance.getId() != null) {
            tags.put("instanceId", instance.getId().toString());
        }
        if (cycleLogId != null) {
            tags.put("cycleLogId", cycleLogId.toString());
        }
        return tags;
    }

    private static String instanceLine(StrategyInstanceEntity instance) {
        if (instance == null) {
            return "instance=?";
        }
        String name = instance.getName();
        String id = instance.getId() != null ? instance.getId().toString() : "?";
        return "instance=" + (name != null ? name : "?") + "(" + id + ")";
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
