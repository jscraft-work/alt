package work.jscraft.alt.trading.infrastructure.persistence;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;

@Entity
@Table(name = "trade_cycle_log")
public class TradeCycleLogEntity extends CreatedAtOnlyUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_instance_id", nullable = false)
    private StrategyInstanceEntity strategyInstance;

    @Column(name = "cycle_started_at", nullable = false)
    private OffsetDateTime cycleStartedAt;

    @Column(name = "cycle_finished_at")
    private OffsetDateTime cycleFinishedAt;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "cycle_stage", nullable = false, length = 40)
    private String cycleStage;

    @Column(name = "failure_reason", length = 120)
    private String failureReason;

    @Column(name = "failure_detail", columnDefinition = "text")
    private String failureDetail;

    @Column(name = "auto_paused_reason", length = 40)
    private String autoPausedReason;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = OffsetDateTime.now();
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

    public String getCycleStage() {
        return cycleStage;
    }

    public void setCycleStage(String cycleStage) {
        this.cycleStage = cycleStage;
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

    public String getAutoPausedReason() {
        return autoPausedReason;
    }

    public void setAutoPausedReason(String autoPausedReason) {
        this.autoPausedReason = autoPausedReason;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
