package work.jscraft.alt.trading.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;

@Entity
@Table(name = "trade_decision_log")
public class TradeDecisionLogEntity extends CreatedAtOnlyUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_cycle_log_id", nullable = false)
    private TradeCycleLogEntity tradeCycleLog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_instance_id", nullable = false)
    private StrategyInstanceEntity strategyInstance;

    @Column(name = "cycle_started_at", nullable = false)
    private OffsetDateTime cycleStartedAt;

    @Column(name = "cycle_finished_at", nullable = false)
    private OffsetDateTime cycleFinishedAt;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "cycle_status", nullable = false, length = 20)
    private String cycleStatus;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "failure_reason", length = 120)
    private String failureReason;

    @Column(name = "failure_detail", columnDefinition = "text")
    private String failureDetail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode settingsSnapshotJson;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "engine_name", length = 40)
    private String engineName;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "request_text", columnDefinition = "text")
    private String requestText;

    @Column(name = "response_text", columnDefinition = "text")
    private String responseText;

    @Column(name = "stdout_text", columnDefinition = "text")
    private String stdoutText;

    @Column(name = "stderr_text", columnDefinition = "text")
    private String stderrText;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "estimated_cost", precision = 19, scale = 8)
    private BigDecimal estimatedCost;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "call_status", length = 30)
    private String callStatus;

    @Column(name = "box_low", precision = 19, scale = 8)
    private BigDecimal boxLow;

    @Column(name = "box_high", precision = 19, scale = 8)
    private BigDecimal boxHigh;

    @Column(name = "box_confidence", precision = 5, scale = 4)
    private BigDecimal boxConfidence;

    public TradeCycleLogEntity getTradeCycleLog() {
        return tradeCycleLog;
    }

    public void setTradeCycleLog(TradeCycleLogEntity tradeCycleLog) {
        this.tradeCycleLog = tradeCycleLog;
    }

    public StrategyInstanceEntity getStrategyInstance() {
        return strategyInstance;
    }

    public void setStrategyInstance(StrategyInstanceEntity strategyInstance) {
        this.strategyInstance = strategyInstance;
    }

    public OffsetDateTime getCycleStartedAt() {
        return cycleStartedAt;
    }

    public void setCycleStartedAt(OffsetDateTime cycleStartedAt) {
        this.cycleStartedAt = cycleStartedAt;
    }

    public OffsetDateTime getCycleFinishedAt() {
        return cycleFinishedAt;
    }

    public void setCycleFinishedAt(OffsetDateTime cycleFinishedAt) {
        this.cycleFinishedAt = cycleFinishedAt;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }

    public String getCycleStatus() {
        return cycleStatus;
    }

    public void setCycleStatus(String cycleStatus) {
        this.cycleStatus = cycleStatus;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getFailureDetail() {
        return failureDetail;
    }

    public void setFailureDetail(String failureDetail) {
        this.failureDetail = failureDetail;
    }

    public JsonNode getSettingsSnapshotJson() {
        return settingsSnapshotJson;
    }

    public void setSettingsSnapshotJson(JsonNode settingsSnapshotJson) {
        this.settingsSnapshotJson = settingsSnapshotJson;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getEngineName() {
        return engineName;
    }

    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getRequestText() {
        return requestText;
    }

    public void setRequestText(String requestText) {
        this.requestText = requestText;
    }

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public String getStdoutText() {
        return stdoutText;
    }

    public void setStdoutText(String stdoutText) {
        this.stdoutText = stdoutText;
    }

    public String getStderrText() {
        return stderrText;
    }

    public void setStderrText(String stderrText) {
        this.stderrText = stderrText;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public String getCallStatus() {
        return callStatus;
    }

    public void setCallStatus(String callStatus) {
        this.callStatus = callStatus;
    }

    public BigDecimal getBoxLow() {
        return boxLow;
    }

    public void setBoxLow(BigDecimal boxLow) {
        this.boxLow = boxLow;
    }

    public BigDecimal getBoxHigh() {
        return boxHigh;
    }

    public void setBoxHigh(BigDecimal boxHigh) {
        this.boxHigh = boxHigh;
    }

    public BigDecimal getBoxConfidence() {
        return boxConfidence;
    }

    public void setBoxConfidence(BigDecimal boxConfidence) {
        this.boxConfidence = boxConfidence;
    }
}
