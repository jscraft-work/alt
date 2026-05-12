package work.jscraft.alt.strategy.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import work.jscraft.alt.common.persistence.SoftDeletableUuidEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;

@Entity
@Table(name = "strategy_instance_watchlist_relation")
@SQLDelete(sql = """
        UPDATE strategy_instance_watchlist_relation
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = ? AND version = ?
        """)
@SQLRestriction("deleted_at is null")
public class StrategyInstanceWatchlistRelationEntity extends SoftDeletableUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_instance_id", nullable = false)
    private StrategyInstanceEntity strategyInstance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_master_id", nullable = false)
    private AssetMasterEntity assetMaster;

    public StrategyInstanceEntity getStrategyInstance() {
        return strategyInstance;
    }

    public void setStrategyInstance(StrategyInstanceEntity strategyInstance) {
        this.strategyInstance = strategyInstance;
    }

    public AssetMasterEntity getAssetMaster() {
        return assetMaster;
    }

    public void setAssetMaster(AssetMasterEntity assetMaster) {
        this.assetMaster = assetMaster;
    }
}
