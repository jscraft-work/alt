package work.jscraft.alt.trading.application.cycle;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeCycleLogRepository;

@Service
public class TradeCycleLifecycle {

    public static final String STAGE_READY = "READY";
    public static final String STAGE_RECONCILE_PENDING = "RECONCILE_PENDING";
    public static final String STAGE_INPUT_LOADING = "INPUT_LOADING";
    public static final String STAGE_LLM_CALLING = "LLM_CALLING";
    public static final String STAGE_DECISION_VALIDATING = "DECISION_VALIDATING";
    public static final String STAGE_ORDER_PLANNING = "ORDER_PLANNING";
    public static final String STAGE_ORDER_EXECUTING = "ORDER_EXECUTING";
    public static final String STAGE_PORTFOLIO_UPDATING = "PORTFOLIO_UPDATING";
    public static final String STAGE_COMPLETED = "COMPLETED";
    public static final String STAGE_FAILED = "FAILED";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TradeCycleLogRepository tradeCycleLogRepository;
    private final Clock clock;

    public TradeCycleLifecycle(TradeCycleLogRepository tradeCycleLogRepository, Clock clock) {
        this.tradeCycleLogRepository = tradeCycleLogRepository;
        this.clock = clock;
    }

    @Transactional
    public UUID startCycle(StrategyInstanceEntity instance) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        TradeCycleLogEntity log = new TradeCycleLogEntity();
        log.setStrategyInstance(instance);
        log.setCycleStartedAt(now);
        log.setBusinessDate(now.atZoneSameInstant(KST).toLocalDate());
        log.setCycleStage(STAGE_READY);
        return tradeCycleLogRepository.saveAndFlush(log).getId();
    }

    @Transactional
    public void advance(UUID cycleLogId, String stage) {
        TradeCycleLogEntity log = require(cycleLogId);
        log.setCycleStage(stage);
        tradeCycleLogRepository.saveAndFlush(log);
    }

    @Transactional
    public void complete(UUID cycleLogId) {
        TradeCycleLogEntity log = require(cycleLogId);
        log.setCycleStage(STAGE_COMPLETED);
        log.setCycleFinishedAt(OffsetDateTime.now(clock));
        tradeCycleLogRepository.saveAndFlush(log);
    }

    @Transactional
    public void fail(UUID cycleLogId, String reason, String detail) {
        TradeCycleLogEntity log = require(cycleLogId);
        log.setCycleStage(STAGE_FAILED);
        log.setFailureReason(reason);
        log.setFailureDetail(detail);
        log.setCycleFinishedAt(OffsetDateTime.now(clock));
        tradeCycleLogRepository.saveAndFlush(log);
    }

    @Transactional
    public void failWithAutoPause(UUID cycleLogId, String reason, String detail, String autoPausedReason) {
        TradeCycleLogEntity log = require(cycleLogId);
        log.setCycleStage(STAGE_FAILED);
        log.setFailureReason(reason);
        log.setFailureDetail(detail);
        log.setAutoPausedReason(autoPausedReason);
        log.setCycleFinishedAt(OffsetDateTime.now(clock));
        tradeCycleLogRepository.saveAndFlush(log);
    }

    private TradeCycleLogEntity require(UUID cycleLogId) {
        return tradeCycleLogRepository.findById(cycleLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "trade_cycle_log을 찾을 수 없습니다: " + cycleLogId));
    }
}
