package work.jscraft.alt.marketdata.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import work.jscraft.alt.common.persistence.SoftDeletableUuidEntity;

@Entity
@Table(name = "asset_master")
@SQLDelete(sql = """
        UPDATE asset_master
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = ? AND version = ?
        """)
@SQLRestriction("deleted_at is null")
public class AssetMasterEntity extends SoftDeletableUuidEntity {

    @Column(name = "symbol_code", nullable = false, length = 40)
    private String symbolCode;

    @Column(name = "symbol_name", nullable = false, length = 200)
    private String symbolName;

    @Column(name = "market_type", nullable = false, length = 40)
    private String marketType;

    @Column(name = "dart_corp_code", length = 20)
    private String dartCorpCode;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    public String getSymbolCode() {
        return symbolCode;
    }

    public void setSymbolCode(String symbolCode) {
        this.symbolCode = symbolCode;
    }

    public String getSymbolName() {
        return symbolName;
    }

    public void setSymbolName(String symbolName) {
        this.symbolName = symbolName;
    }

    public String getMarketType() {
        return marketType;
    }

    public void setMarketType(String marketType) {
        this.marketType = marketType;
    }

    public String getDartCorpCode() {
        return dartCorpCode;
    }

    public void setDartCorpCode(String dartCorpCode) {
        this.dartCorpCode = dartCorpCode;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
