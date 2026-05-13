package work.jscraft.alt.trading.infrastructure.persistence;

import java.math.BigDecimal;

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

@Entity
@Table(name = "trade_order_intent")
public class TradeOrderIntentEntity extends CreatedAtOnlyUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_decision_log_id", nullable = false)
    private TradeDecisionLogEntity tradeDecisionLog;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "symbol_code", nullable = false, length = 40)
    private String symbolCode;

    @Column(name = "symbol_name", nullable = false, length = 200)
    private String symbolName;

    @Column(nullable = false, length = 10)
    private String side;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    @Column(precision = 19, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, columnDefinition = "text")
    private String rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode evidenceJson;

    @Column(name = "execution_blocked_reason", length = 120)
    private String executionBlockedReason;

    public TradeDecisionLogEntity getTradeDecisionLog() {
        return tradeDecisionLog;
    }

    public void setTradeDecisionLog(TradeDecisionLogEntity tradeDecisionLog) {
        this.tradeDecisionLog = tradeDecisionLog;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

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

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public JsonNode getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(JsonNode evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public String getExecutionBlockedReason() {
        return executionBlockedReason;
    }

    public void setExecutionBlockedReason(String executionBlockedReason) {
        this.executionBlockedReason = executionBlockedReason;
    }
}
