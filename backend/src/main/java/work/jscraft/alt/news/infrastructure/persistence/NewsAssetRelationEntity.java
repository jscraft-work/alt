package work.jscraft.alt.news.infrastructure.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;

@Entity
@Table(name = "news_asset_relation")
public class NewsAssetRelationEntity extends CreatedAtOnlyUuidEntity {

    @Column(name = "news_item_id", nullable = false)
    private UUID newsItemId;

    @Column(name = "asset_master_id", nullable = false)
    private UUID assetMasterId;

    public UUID getNewsItemId() {
        return newsItemId;
    }

    public void setNewsItemId(UUID newsItemId) {
        this.newsItemId = newsItemId;
    }

    public UUID getAssetMasterId() {
        return assetMasterId;
    }

    public void setAssetMasterId(UUID assetMasterId) {
        this.assetMasterId = assetMasterId;
    }
}
