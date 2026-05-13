package work.jscraft.alt;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import work.jscraft.alt.ops.application.AlertEvent;
import work.jscraft.alt.ops.application.AlertEvent.Severity;
import work.jscraft.alt.ops.application.AlertGateway;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.decision.DecisionParseException;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.ops.TradingIncidentReporter;
import work.jscraft.alt.trading.application.ops.TradingOpsEventRecorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradingIncidentReporterTest {

    private AlertGateway alertGateway;
    private OpsEventRepository opsEventRepository;
    private TradingIncidentReporter reporter;
    private Clock clock;

    @BeforeEach
    void setUp() {
        alertGateway = mock(AlertGateway.class);
        opsEventRepository = mock(OpsEventRepository.class);
        when(opsEventRepository.saveAndFlush(any(OpsEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        clock = Clock.fixed(Instant.parse("2026-05-12T09:00:00Z"), ZoneId.of("Asia/Seoul"));
        TradingOpsEventRecorder opsEventRecorder =
                new TradingOpsEventRecorder(opsEventRepository, new ObjectMapper(), clock);
        reporter = new TradingIncidentReporter(alertGateway, opsEventRecorder, clock);
    }

    @Test
    void llmFailure_writesOpsEventAndAlertWithStatusTag() throws Exception {
        StrategyInstanceEntity instance = instance("my-strategy");
        UUID cycleLogId = UUID.randomUUID();
        LlmCallResult result = LlmCallResult.timeout("subprocess timed out");

        reporter.reportLlmFailure(instance, cycleLogId, result);

        ArgumentCaptor<OpsEventEntity> opsCaptor = ArgumentCaptor.forClass(OpsEventEntity.class);
        verify(opsEventRepository).saveAndFlush(opsCaptor.capture());
        OpsEventEntity opsEvent = opsCaptor.getValue();
        assertThat(opsEvent.getServiceName()).isEqualTo(TradingOpsEventRecorder.SERVICE);
        assertThat(opsEvent.getEventType()).isEqualTo(TradingOpsEventRecorder.EVENT_LLM_FAILED);
        assertThat(opsEvent.getMessage()).contains("LLM_TIMEOUT").contains("subprocess timed out");
        assertThat(((ObjectNode) opsEvent.getPayloadJson()).get("status").asText()).isEqualTo("TIMEOUT");

        ArgumentCaptor<AlertEvent> alertCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertGateway).dispatch(alertCaptor.capture());
        AlertEvent alert = alertCaptor.getValue();
        assertThat(alert.severity()).isEqualTo(Severity.WARNING);
        assertThat(alert.title()).isEqualTo("LLM 호출 실패");
        assertThat(alert.tags().get("instanceId")).isEqualTo(instance.getId().toString());
        assertThat(alert.tags().get("cycleLogId")).isEqualTo(cycleLogId.toString());
    }

    @Test
    void autoPausedAlertIsCritical() throws Exception {
        StrategyInstanceEntity instance = instance("live-1");

        reporter.reportAutoPaused(instance, "reconcile_failed");

        ArgumentCaptor<AlertEvent> alertCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertGateway).dispatch(alertCaptor.capture());
        assertThat(alertCaptor.getValue().severity()).isEqualTo(Severity.CRITICAL);
        assertThat(alertCaptor.getValue().title()).isEqualTo("자동 일시중지");
        assertThat(alertCaptor.getValue().message()).contains("reconcile_failed");
    }

    @Test
    void alertDispatchExceptionIsSwallowedSoOpsEventStillWritten() throws Exception {
        StrategyInstanceEntity instance = instance("flaky");
        when(alertGateway.dispatch(any(AlertEvent.class)))
                .thenThrow(new RuntimeException("telegram down"));

        reporter.reportReconcileFailure(instance, "broker timeout");

        verify(opsEventRepository).saveAndFlush(any(OpsEventEntity.class));
        verify(alertGateway).dispatch(any(AlertEvent.class));
    }

    @Test
    void decisionParseFailureUsesReasonTag() throws Exception {
        StrategyInstanceEntity instance = instance("parse-fail");
        UUID cycleLogId = UUID.randomUUID();
        DecisionParseException ex = new DecisionParseException(
                DecisionParseException.Reason.INVALID_JSON, "bad json");

        reporter.reportDecisionParseFailure(instance, cycleLogId, ex);

        ArgumentCaptor<OpsEventEntity> opsCaptor = ArgumentCaptor.forClass(OpsEventEntity.class);
        verify(opsEventRepository).saveAndFlush(opsCaptor.capture());
        assertThat(opsCaptor.getValue().getEventType())
                .isEqualTo(TradingOpsEventRecorder.EVENT_DECISION_PARSE_FAILED);
        assertThat(((ObjectNode) opsCaptor.getValue().getPayloadJson()).get("reason").asText())
                .isEqualTo("INVALID_JSON");
    }

    private StrategyInstanceEntity instance(String name) throws Exception {
        StrategyInstanceEntity entity = new StrategyInstanceEntity();
        entity.setName(name);
        Class<?> klass = entity.getClass();
        while (klass != null) {
            try {
                Field f = klass.getDeclaredField("id");
                f.setAccessible(true);
                f.set(entity, UUID.randomUUID());
                return entity;
            } catch (NoSuchFieldException ignored) {
                klass = klass.getSuperclass();
            }
        }
        throw new IllegalStateException("no 'id' field on " + entity.getClass());
    }
}
