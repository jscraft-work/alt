package work.jscraft.alt.portfolio.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "portfolio")
public class PortfolioEntity {

    @Id
    @Column(name = "strategy_instance_id", nullable = false, updatable = false)
    private UUID strategyInstanceId;

    @Column(name = "cash_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashAmount;

    @Column(name = "total_asset_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAssetAmount;

    @Column(name = "realized_pnl_today", nullable = false, precision = 19, scale = 4)
    private BigDecimal realizedPnlToday;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getStrategyInstanceId() {
        return strategyInstanceId;
    }

    public void setStrategyInstanceId(UUID strategyInstanceId) {
        this.strategyInstanceId = strategyInstanceId;
    }

    public BigDecimal getCashAmount() {
        return cashAmount;
    }

    public void setCashAmount(BigDecimal cashAmount) {
        this.cashAmount = cashAmount;
    }

    public BigDecimal getTotalAssetAmount() {
        return totalAssetAmount;
    }

    public void setTotalAssetAmount(BigDecimal totalAssetAmount) {
        this.totalAssetAmount = totalAssetAmount;
    }

    public BigDecimal getRealizedPnlToday() {
        return realizedPnlToday;
    }

    public void setRealizedPnlToday(BigDecimal realizedPnlToday) {
        this.realizedPnlToday = realizedPnlToday;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
