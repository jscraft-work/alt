package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;

@Entity
@Table(name = "market_minute_item")
public class MarketMinuteItemEntity extends CreatedAtOnlyUuidEntity {

    @Column(name = "symbol_code", nullable = false, length = 40)
    private String symbolCode;

    @Column(name = "bar_time", nullable = false)
    private OffsetDateTime barTime;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "open_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal closePrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal volume;

    @Column(name = "source_name", nullable = false, length = 40)
    private String sourceName;

    public String getSymbolCode() {
        return symbolCode;
    }

    public void setSymbolCode(String symbolCode) {
        this.symbolCode = symbolCode;
    }

    public OffsetDateTime getBarTime() {
        return barTime;
    }

    public void setBarTime(OffsetDateTime barTime) {
        this.barTime = barTime;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
}
