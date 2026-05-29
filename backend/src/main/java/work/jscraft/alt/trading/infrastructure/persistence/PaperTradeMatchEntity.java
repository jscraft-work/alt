package work.jscraft.alt.trading.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;

/**
 * V18 — SELL paper 체결 시 PaperTradeMatcher 가 FIFO 매칭으로 INSERT.
 *
 * <p>multi-row 가능 — 같은 종목 다중 BUY 후 1 회 SELL 시 BUY 잔량 따라 여러 row 생성.
 *
 * <p>{@code gross_pnl_pct} = 의도가 기반 (slippage 없다 가정 시).
 * {@code net_pnl_pct} = paper_actual_amount 기반 (slippage + 세금 + 수수료 차감 후, 실제 현금 흐름).
 * 둘 사이 차이 = cost wall 실측.
 */
@Entity
@Table(name = "paper_trade_match")
public class PaperTradeMatchEntity extends CreatedAtOnlyUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_instance_id", nullable = false)
    private StrategyInstanceEntity strategyInstance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buy_trade_order_id", nullable = false)
    private TradeOrderEntity buyTradeOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sell_trade_order_id", nullable = false)
    private TradeOrderEntity sellTradeOrder;

    @Column(name = "symbol_code", nullable = false, length = 40)
    private String symbolCode;

    @Column(name = "matched_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal matchedQuantity;

    @Column(name = "entry_time", nullable = false)
    private OffsetDateTime entryTime;

    @Column(name = "exit_time", nullable = false)
    private OffsetDateTime exitTime;

    @Column(name = "holding_minutes", nullable = false)
    private int holdingMinutes;

    @Column(name = "gross_pnl_pct", nullable = false, precision = 10, scale = 6)
    private BigDecimal grossPnlPct;

    @Column(name = "net_pnl_pct", nullable = false, precision = 10, scale = 6)
    private BigDecimal netPnlPct;

    @Column(name = "slippage_buy_pct", precision = 10, scale = 6)
    private BigDecimal slippageBuyPct;

    @Column(name = "slippage_sell_pct", precision = 10, scale = 6)
    private BigDecimal slippageSellPct;

    @Column(name = "sell_tax_pct", precision = 10, scale = 6)
    private BigDecimal sellTaxPct;

    @Column(name = "fee_pct", precision = 10, scale = 6)
    private BigDecimal feePct;

    public StrategyInstanceEntity getStrategyInstance() {
        return strategyInstance;
    }

    public void setStrategyInstance(StrategyInstanceEntity strategyInstance) {
        this.strategyInstance = strategyInstance;
    }

    public TradeOrderEntity getBuyTradeOrder() {
        return buyTradeOrder;
    }

    public void setBuyTradeOrder(TradeOrderEntity buyTradeOrder) {
        this.buyTradeOrder = buyTradeOrder;
    }

    public TradeOrderEntity getSellTradeOrder() {
        return sellTradeOrder;
    }

    public void setSellTradeOrder(TradeOrderEntity sellTradeOrder) {
        this.sellTradeOrder = sellTradeOrder;
    }

    public String getSymbolCode() {
        return symbolCode;
    }

    public void setSymbolCode(String symbolCode) {
        this.symbolCode = symbolCode;
    }

    public BigDecimal getMatchedQuantity() {
        return matchedQuantity;
    }

    public void setMatchedQuantity(BigDecimal matchedQuantity) {
        this.matchedQuantity = matchedQuantity;
    }

    public OffsetDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(OffsetDateTime entryTime) {
        this.entryTime = entryTime;
    }

    public OffsetDateTime getExitTime() {
        return exitTime;
    }

    public void setExitTime(OffsetDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public int getHoldingMinutes() {
        return holdingMinutes;
    }

    public void setHoldingMinutes(int holdingMinutes) {
        this.holdingMinutes = holdingMinutes;
    }

    public BigDecimal getGrossPnlPct() {
        return grossPnlPct;
    }

    public void setGrossPnlPct(BigDecimal grossPnlPct) {
        this.grossPnlPct = grossPnlPct;
    }

    public BigDecimal getNetPnlPct() {
        return netPnlPct;
    }

    public void setNetPnlPct(BigDecimal netPnlPct) {
        this.netPnlPct = netPnlPct;
    }

    public BigDecimal getSlippageBuyPct() {
        return slippageBuyPct;
    }

    public void setSlippageBuyPct(BigDecimal slippageBuyPct) {
        this.slippageBuyPct = slippageBuyPct;
    }

    public BigDecimal getSlippageSellPct() {
        return slippageSellPct;
    }

    public void setSlippageSellPct(BigDecimal slippageSellPct) {
        this.slippageSellPct = slippageSellPct;
    }

    public BigDecimal getSellTaxPct() {
        return sellTaxPct;
    }

    public void setSellTaxPct(BigDecimal sellTaxPct) {
        this.sellTaxPct = sellTaxPct;
    }

    public BigDecimal getFeePct() {
        return feePct;
    }

    public void setFeePct(BigDecimal feePct) {
        this.feePct = feePct;
    }
}
