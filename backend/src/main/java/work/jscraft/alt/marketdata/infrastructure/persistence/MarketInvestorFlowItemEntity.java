package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;

/**
 * KIS inquire-investor (FHKST01010900) 응답의 종목별 일별 투자자 순매수 (개인/외국인/기관계).
 * 한 행 = 한 종목 × 하루. 수량은 주, 거래대금({@code *_ntby_amt})은 백만원 단위.
 */
@Entity
@Table(name = "market_investor_flow_item")
public class MarketInvestorFlowItemEntity extends CreatedAtOnlyUuidEntity {

    @Column(name = "symbol_code", nullable = false, length = 40)
    private String symbolCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "source_name", nullable = false, length = 40)
    private String sourceName;

    @Column(name = "close_price")
    private Integer closePrice;

    @Column(name = "indv_ntby_qty")
    private Long indvNtbyQty;

    @Column(name = "frgn_ntby_qty")
    private Long frgnNtbyQty;

    @Column(name = "orgn_ntby_qty")
    private Long orgnNtbyQty;

    @Column(name = "indv_ntby_amt")
    private Long indvNtbyAmt;

    @Column(name = "frgn_ntby_amt")
    private Long frgnNtbyAmt;

    @Column(name = "orgn_ntby_amt")
    private Long orgnNtbyAmt;

    public String getSymbolCode() { return symbolCode; }
    public void setSymbolCode(String v) { this.symbolCode = v; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate v) { this.tradeDate = v; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String v) { this.sourceName = v; }
    public Integer getClosePrice() { return closePrice; }
    public void setClosePrice(Integer v) { this.closePrice = v; }
    public Long getIndvNtbyQty() { return indvNtbyQty; }
    public void setIndvNtbyQty(Long v) { this.indvNtbyQty = v; }
    public Long getFrgnNtbyQty() { return frgnNtbyQty; }
    public void setFrgnNtbyQty(Long v) { this.frgnNtbyQty = v; }
    public Long getOrgnNtbyQty() { return orgnNtbyQty; }
    public void setOrgnNtbyQty(Long v) { this.orgnNtbyQty = v; }
    public Long getIndvNtbyAmt() { return indvNtbyAmt; }
    public void setIndvNtbyAmt(Long v) { this.indvNtbyAmt = v; }
    public Long getFrgnNtbyAmt() { return frgnNtbyAmt; }
    public void setFrgnNtbyAmt(Long v) { this.frgnNtbyAmt = v; }
    public Long getOrgnNtbyAmt() { return orgnNtbyAmt; }
    public void setOrgnNtbyAmt(Long v) { this.orgnNtbyAmt = v; }
}
