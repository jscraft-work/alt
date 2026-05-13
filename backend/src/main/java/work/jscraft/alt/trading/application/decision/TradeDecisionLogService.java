package work.jscraft.alt.trading.application.decision;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.trading.application.cycle.SettingsSnapshot;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeDecisionLogRepository;

@Service
public class TradeDecisionLogService {

    public static final String STATUS_EXECUTE = "EXECUTE";
    public static final String STATUS_HOLD = "HOLD";
    public static final String STATUS_FAILED = "FAILED";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TradeDecisionLogRepository tradeDecisionLogRepository;
    private final TradeCycleLogRepository tradeCycleLogRepository;
    private final Clock clock;

    public TradeDecisionLogService(
            TradeDecisionLogRepository tradeDecisionLogRepository,
            TradeCycleLogRepository tradeCycleLogRepository,
            Clock clock) {
        this.tradeDecisionLogRepository = tradeDecisionLogRepository;
        this.tradeCycleLogRepository = tradeCycleLogRepository;
        this.clock = clock;
    }

    @Transactional
    public TradeDecisionLogEntity saveSuccess(
            UUID cycleLogId,
            ParsedDecision decision,
            SettingsSnapshot snapshot,
            JsonNode settingsSnapshotJson,
            LlmRequest request,
            LlmCallResult result,
            LlmUsage usage) {
        TradeCycleLogEntity cycleLog = requireCycleLog(cycleLogId);
        TradeDecisionLogEntity log = baseLog(cycleLog, snapshot, settingsSnapshotJson, request, result, usage);
        log.setCycleStatus(decision.cycleStatus());
        log.setSummary(decision.summary());
        log.setConfidence(decision.confidence());
        if (decision.boxEstimate() != null) {
            log.setBoxLow(decision.boxEstimate().low());
            log.setBoxHigh(decision.boxEstimate().high());
            log.setBoxConfidence(decision.boxEstimate().confidence());
        }
        return tradeDecisionLogRepository.saveAndFlush(log);
    }

    @Transactional
    public TradeDecisionLogEntity saveFailure(
            UUID cycleLogId,
            SettingsSnapshot snapshot,
            JsonNode settingsSnapshotJson,
            LlmRequest request,
            LlmCallResult result,
            LlmUsage usage,
            String failureReason,
            String failureDetail) {
        TradeCycleLogEntity cycleLog = requireCycleLog(cycleLogId);
        TradeDecisionLogEntity log = baseLog(cycleLog, snapshot, settingsSnapshotJson, request, result, usage);
        log.setCycleStatus(STATUS_FAILED);
        log.setFailureReason(failureReason);
        log.setFailureDetail(failureDetail);
        return tradeDecisionLogRepository.saveAndFlush(log);
    }

    private TradeDecisionLogEntity baseLog(
            TradeCycleLogEntity cycleLog,
            SettingsSnapshot snapshot,
            JsonNode settingsSnapshotJson,
            LlmRequest request,
            LlmCallResult result,
            LlmUsage usage) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        TradeDecisionLogEntity log = new TradeDecisionLogEntity();
        log.setTradeCycleLog(cycleLog);
        log.setStrategyInstance(cycleLog.getStrategyInstance());
        log.setCycleStartedAt(cycleLog.getCycleStartedAt());
        log.setCycleFinishedAt(now);
        log.setBusinessDate(cycleLog.getCycleStartedAt().atZoneSameInstant(KST).toLocalDate());
        log.setSettingsSnapshotJson(settingsSnapshotJson);
        log.setSessionId(request.sessionId());
        log.setEngineName(request.engineName());
        log.setModelName(request.modelName());
        log.setRequestText(request.promptText());
        log.setResponseText(result.stdoutText());
        log.setStdoutText(result.stdoutText());
        log.setStderrText(result.stderrText());
        log.setTimeoutSeconds((int) request.timeout().toSeconds());
        log.setExitCode(result.exitCode());
        log.setCallStatus(result.callStatus().name());
        if (usage != null) {
            log.setInputTokens(usage.inputTokens());
            log.setOutputTokens(usage.outputTokens());
            log.setEstimatedCost(usage.estimatedCost());
        }
        return log;
    }

    private TradeCycleLogEntity requireCycleLog(UUID cycleLogId) {
        return tradeCycleLogRepository.findById(cycleLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "trade_cycle_log을 찾을 수 없습니다: " + cycleLogId));
    }

    public record LlmUsage(Integer inputTokens, Integer outputTokens, java.math.BigDecimal estimatedCost) {
    }
}
