package work.jscraft.alt.trading.application.cycle;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.trading.application.decision.DecisionParseException;
import work.jscraft.alt.trading.application.decision.DecisionParser;
import work.jscraft.alt.trading.application.decision.LlmCallResult;
import work.jscraft.alt.trading.application.decision.LlmExecutableProperties;
import work.jscraft.alt.trading.application.decision.LlmRequest;
import work.jscraft.alt.trading.application.decision.OrderIntentSafetyValidator;
import work.jscraft.alt.trading.application.decision.ParsedDecision;
import work.jscraft.alt.trading.application.decision.TradeDecisionLogService;
import work.jscraft.alt.trading.application.decision.TradeDecisionLogService.LlmUsage;
import work.jscraft.alt.trading.application.decision.TradeOrderIntentGenerator;
import work.jscraft.alt.trading.application.decision.TradingDecisionEngine;
import work.jscraft.alt.trading.application.inputspec.InputAssembler;
import work.jscraft.alt.trading.application.ops.TradingIncidentReporter;
import work.jscraft.alt.trading.application.order.LiveOrderExecutor;
import work.jscraft.alt.trading.application.order.PaperOrderExecutor;
import work.jscraft.alt.trading.application.reconcile.ReconcileService;
import work.jscraft.alt.trading.application.reconcile.ReconcileService.ReconcileResult;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;

@Service
public class CycleExecutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CycleExecutionOrchestrator.class);
    public static final String LIFECYCLE_STATE_ACTIVE = "active";
    public static final String EXECUTION_MODE_PAPER = "paper";
    public static final String EXECUTION_MODE_LIVE = "live";

    private final StrategyInstanceRepository strategyInstanceRepository;
    private final TradingWindowPolicy tradingWindowPolicy;
    private final SettingsSnapshotProvider snapshotProvider;
    private final TradeCycleLifecycle lifecycle;
    private final InputAssembler inputAssembler;
    private final TradingDecisionEngine decisionEngine;
    private final DecisionParser decisionParser;
    private final TradeDecisionLogService decisionLogService;
    private final TradeOrderIntentGenerator intentGenerator;
    private final OrderIntentSafetyValidator safetyValidator;
    private final PaperOrderExecutor paperOrderExecutor;
    private final LiveOrderExecutor liveOrderExecutor;
    private final ReconcileService reconcileService;
    private final LlmModelProfileRepository llmModelProfileRepository;
    private final LlmExecutableProperties llmExecutableProperties;
    private final TradingIncidentReporter incidentReporter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CycleExecutionOrchestrator(
            StrategyInstanceRepository strategyInstanceRepository,
            TradingWindowPolicy tradingWindowPolicy,
            SettingsSnapshotProvider snapshotProvider,
            TradeCycleLifecycle lifecycle,
            InputAssembler inputAssembler,
            TradingDecisionEngine decisionEngine,
            DecisionParser decisionParser,
            TradeDecisionLogService decisionLogService,
            TradeOrderIntentGenerator intentGenerator,
            OrderIntentSafetyValidator safetyValidator,
            PaperOrderExecutor paperOrderExecutor,
            LiveOrderExecutor liveOrderExecutor,
            ReconcileService reconcileService,
            LlmModelProfileRepository llmModelProfileRepository,
            LlmExecutableProperties llmExecutableProperties,
            TradingIncidentReporter incidentReporter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.strategyInstanceRepository = strategyInstanceRepository;
        this.tradingWindowPolicy = tradingWindowPolicy;
        this.snapshotProvider = snapshotProvider;
        this.lifecycle = lifecycle;
        this.inputAssembler = inputAssembler;
        this.decisionEngine = decisionEngine;
        this.decisionParser = decisionParser;
        this.decisionLogService = decisionLogService;
        this.intentGenerator = intentGenerator;
        this.safetyValidator = safetyValidator;
        this.paperOrderExecutor = paperOrderExecutor;
        this.liveOrderExecutor = liveOrderExecutor;
        this.reconcileService = reconcileService;
        this.llmModelProfileRepository = llmModelProfileRepository;
        this.llmExecutableProperties = llmExecutableProperties;
        this.incidentReporter = incidentReporter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public CycleResult runOnce(UUID strategyInstanceId, String workerId) {
        Optional<StrategyInstanceEntity> maybeInstance = strategyInstanceRepository.findById(strategyInstanceId);
        if (maybeInstance.isEmpty()) {
            return CycleResult.skipped(SkipReason.INSTANCE_NOT_FOUND);
        }
        StrategyInstanceEntity instance = maybeInstance.get();
        if (!LIFECYCLE_STATE_ACTIVE.equals(instance.getLifecycleState())) {
            return CycleResult.skipped(SkipReason.NOT_ACTIVE);
        }
        if (instance.getAutoPausedReason() != null) {
            return CycleResult.skipped(SkipReason.AUTO_PAUSED);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!tradingWindowPolicy.isWithinWindow(now)) {
            return CycleResult.skipped(SkipReason.OUT_OF_WINDOW);
        }

        SettingsSnapshot snapshot = snapshotProvider.capture(strategyInstanceId);
        UUID cycleLogId = lifecycle.startCycle(instance);
        String nextStage = EXECUTION_MODE_LIVE.equals(instance.getExecutionMode())
                ? TradeCycleLifecycle.STAGE_RECONCILE_PENDING
                : TradeCycleLifecycle.STAGE_INPUT_LOADING;
        lifecycle.advance(cycleLogId, nextStage);

        if (EXECUTION_MODE_LIVE.equals(instance.getExecutionMode())) {
            ReconcileResult reconcileResult = reconcileService.reconcile(instance);
            if (!reconcileResult.success()) {
                incidentReporter.reportReconcileFailure(instance, reconcileResult.failureMessage());
                reconcileService.markAutoPaused(instance.getId(), ReconcileService.AUTO_PAUSED_REASON);
                lifecycle.failWithAutoPause(cycleLogId, "RECONCILE_FAILED",
                        reconcileResult.failureMessage(), ReconcileService.AUTO_PAUSED_REASON);
                return CycleResult.failed(cycleLogId, reconcileResult.failureMessage());
            }
            lifecycle.advance(cycleLogId, TradeCycleLifecycle.STAGE_INPUT_LOADING);
            return runMainPipeline(instance, cycleLogId, snapshot, true);
        }

        return runMainPipeline(instance, cycleLogId, snapshot, false);
    }

    private CycleResult runMainPipeline(
            StrategyInstanceEntity instance,
            UUID cycleLogId,
            SettingsSnapshot snapshot,
            boolean liveMode) {
        try {
            inputAssembler.assemble(snapshot);
        } catch (RuntimeException ex) {
            log.warn("input assembly failed instance={} message={}", instance.getId(), ex.getMessage());
            lifecycle.fail(cycleLogId, "INPUT_ASSEMBLY_FAILED", ex.getMessage());
            return CycleResult.failed(cycleLogId, ex.getMessage());
        }

        lifecycle.advance(cycleLogId, TradeCycleLifecycle.STAGE_LLM_CALLING);
        LlmRequest llmRequest = buildLlmRequest(instance, snapshot);
        LlmCallResult llmResult = decisionEngine.requestTradingDecision(llmRequest);
        JsonNode settingsSnapshotJson = serializeSnapshot(snapshot);

        if (!llmResult.isSuccess()) {
            decisionLogService.saveFailure(
                    cycleLogId, snapshot, settingsSnapshotJson, llmRequest, llmResult, null,
                    "LLM_" + llmResult.callStatus().name(),
                    llmResult.failureMessage());
            lifecycle.fail(cycleLogId, "LLM_" + llmResult.callStatus().name(), llmResult.failureMessage());
            incidentReporter.reportLlmFailure(instance, cycleLogId, llmResult);
            return CycleResult.failed(cycleLogId, llmResult.failureMessage());
        }

        lifecycle.advance(cycleLogId, TradeCycleLifecycle.STAGE_DECISION_VALIDATING);
        ParsedDecision parsed;
        try {
            parsed = decisionParser.parse(llmResult.stdoutText());
        } catch (DecisionParseException ex) {
            decisionLogService.saveFailure(
                    cycleLogId, snapshot, settingsSnapshotJson, llmRequest, llmResult, null,
                    "DECISION_" + ex.reason().name(),
                    ex.getMessage());
            lifecycle.fail(cycleLogId, "DECISION_" + ex.reason().name(), ex.getMessage());
            incidentReporter.reportDecisionParseFailure(instance, cycleLogId, ex);
            return CycleResult.failed(cycleLogId, ex.getMessage());
        }

        TradeDecisionLogEntity decisionLog = decisionLogService.saveSuccess(
                cycleLogId, parsed, snapshot, settingsSnapshotJson, llmRequest, llmResult, null);

        if (TradeDecisionLogService.STATUS_HOLD.equals(parsed.cycleStatus())) {
            lifecycle.complete(cycleLogId);
            return CycleResult.completed(cycleLogId, decisionLog.getId(), parsed.cycleStatus(), List.of());
        }

        lifecycle.advance(cycleLogId, TradeCycleLifecycle.STAGE_ORDER_PLANNING);
        List<TradeOrderIntentEntity> intents = intentGenerator.generate(decisionLog, parsed);
        safetyValidator.validate(instance.getId(), intents);

        lifecycle.advance(cycleLogId, TradeCycleLifecycle.STAGE_ORDER_EXECUTING);
        List<TradeOrderEntity> orders = liveMode
                ? liveOrderExecutor.execute(instance, intents)
                : paperOrderExecutor.execute(instance, intents);

        lifecycle.advance(cycleLogId, TradeCycleLifecycle.STAGE_PORTFOLIO_UPDATING);
        lifecycle.complete(cycleLogId);
        return CycleResult.completed(cycleLogId, decisionLog.getId(), parsed.cycleStatus(), orders);
    }

    private LlmRequest buildLlmRequest(StrategyInstanceEntity instance, SettingsSnapshot snapshot) {
        LlmModelProfileEntity profile = llmModelProfileRepository.findById(snapshot.tradingModelProfileId())
                .orElseThrow(() -> new IllegalStateException(
                        "tradingModelProfile를 찾을 수 없습니다: " + snapshot.tradingModelProfileId()));
        LlmExecutableProperties.Provider executable =
                llmExecutableProperties.forProvider(profile.getProvider());
        return new LlmRequest(
                UUID.randomUUID(),
                profile.getProvider(),
                profile.getModelName(),
                snapshot.promptText(),
                Duration.ofSeconds(Math.max(1, executable.getTimeoutSeconds())));
    }

    private JsonNode serializeSnapshot(SettingsSnapshot snapshot) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("strategyInstanceId", snapshot.strategyInstanceId().toString());
        node.put("executionMode", snapshot.executionMode());
        node.put("promptVersionId", snapshot.promptVersionId().toString());
        node.put("tradingModelProfileId", snapshot.tradingModelProfileId().toString());
        node.set("inputSpec", snapshot.inputSpec() != null ? snapshot.inputSpec() : objectMapper.createObjectNode());
        node.set("executionConfig",
                snapshot.executionConfig() != null ? snapshot.executionConfig() : objectMapper.createObjectNode());
        ArrayNode watchlist = objectMapper.createArrayNode();
        snapshot.watchlistSymbols().forEach(watchlist::add);
        node.set("watchlist", watchlist);
        node.put("capturedAt", snapshot.capturedAt().toString());
        return node;
    }

    public enum SkipReason {
        INSTANCE_NOT_FOUND,
        NOT_ACTIVE,
        AUTO_PAUSED,
        OUT_OF_WINDOW
    }

    public record CycleResult(
            Status status,
            UUID cycleLogId,
            String stage,
            SettingsSnapshot snapshot,
            SkipReason skipReason,
            String failureMessage,
            UUID decisionLogId,
            String cycleStatus,
            List<TradeOrderEntity> orders) {

        public enum Status { STARTED, SKIPPED, FAILED, COMPLETED }

        public static CycleResult started(UUID cycleLogId, String stage, SettingsSnapshot snapshot) {
            return new CycleResult(Status.STARTED, cycleLogId, stage, snapshot, null, null, null, null, List.of());
        }

        public static CycleResult skipped(SkipReason reason) {
            return new CycleResult(Status.SKIPPED, null, null, null, reason, null, null, null, List.of());
        }

        public static CycleResult failed(UUID cycleLogId, String message) {
            return new CycleResult(Status.FAILED, cycleLogId, TradeCycleLifecycle.STAGE_FAILED, null, null, message,
                    null, null, List.of());
        }

        public static CycleResult completed(
                UUID cycleLogId,
                UUID decisionLogId,
                String cycleStatus,
                List<TradeOrderEntity> orders) {
            return new CycleResult(Status.COMPLETED, cycleLogId, TradeCycleLifecycle.STAGE_COMPLETED, null, null, null,
                    decisionLogId, cycleStatus, orders);
        }
    }

}
