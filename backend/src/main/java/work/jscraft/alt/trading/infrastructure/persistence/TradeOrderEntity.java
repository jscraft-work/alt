package work.jscraft.alt.trading.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
@Table(name = "trade_order")
public class TradeOrderEntity extends CreatedAtOnlyUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_order_intent_id", nullable = false)
    private TradeOrderIntentEntity tradeOrderIntent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_instance_id", nullable = false)
    private StrategyInstanceEntity strategyInstance;

    @Column(name = "client_order_id", nullable = false, length = 120)
    private String clientOrderId;

    @Column(name = "broker_order_no", length = 120)
    private String brokerOrderNo;

    @Column(name = "execution_mode", nullable = false, length = 20)
    private String executionMode;

    @Column(name = "order_status", nullable = false, length = 30)
    private String orderStatus;

    @Column(name = "requested_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal requestedQuantity;

    @Column(name = "requested_price", precision = 19, scale = 8)
    private BigDecimal requestedPrice;

    @Column(name = "filled_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Column(name = "avg_filled_price", precision = 19, scale = 8)
    private BigDecimal avgFilledPrice;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "filled_at")
    private OffsetDateTime filledAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "failure_reason", length = 120)
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "broker_response_json", columnDefinition = "jsonb")
    private JsonNode brokerResponseJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "portfolio_after_json", columnDefinition = "jsonb")
    private JsonNode portfolioAfterJson;

    // ----- V17: paper 비용 breakdown (amount 기반) -----
    // invariant (PaperOrderExecutor 에서 enforce):
    //   BUY:  paperActualAmount = paperRequestedAmount + paperSlippageAmount + paperCommissionAmount
    //   SELL: paperActualAmount = paperRequestedAmount - paperSlippageAmount
    //                            - paperSellTaxAmount - paperCommissionAmount
    //   portfolio.cash 변동 절대값 = paperActualAmount

    @Column(name = "paper_requested_amount", precision = 19, scale = 4)
    private BigDecimal paperRequestedAmount;

    @Column(name = "paper_slippage_amount", precision = 19, scale = 4)
    private BigDecimal paperSlippageAmount;

    @Column(name = "paper_sell_tax_amount", precision = 19, scale = 4)
    private BigDecimal paperSellTaxAmount;

    @Column(name = "paper_commission_amount", precision = 19, scale = 4)
    private BigDecimal paperCommissionAmount;

    @Column(name = "paper_actual_amount", precision = 19, scale = 4)
    private BigDecimal paperActualAmount;

    // ----- V17: 호가 walk 결과 -----

    @Column(name = "paper_walk_levels")
    private Short paperWalkLevels;

    @Column(name = "paper_partial_fill_ratio", precision = 6, scale = 4)
    private BigDecimal paperPartialFillRatio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "paper_orderbook_snapshot_json", columnDefinition = "jsonb")
    private JsonNode paperOrderbookSnapshotJson;

    @Column(name = "unfilled_quantity", precision = 19, scale = 8)
    private BigDecimal unfilledQuantity;

    public TradeOrderIntentEntity getTradeOrderIntent() {
        return tradeOrderIntent;
    }

    public void setTradeOrderIntent(TradeOrderIntentEntity tradeOrderIntent) {
        this.tradeOrderIntent = tradeOrderIntent;
    }

    public StrategyInstanceEntity getStrategyInstance() {
        return strategyInstance;
    }

    public void setStrategyInstance(StrategyInstanceEntity strategyInstance) {
        this.strategyInstance = strategyInstance;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public String getBrokerOrderNo() {
        return brokerOrderNo;
    }

    public void setBrokerOrderNo(String brokerOrderNo) {
        this.brokerOrderNo = brokerOrderNo;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public BigDecimal getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(BigDecimal requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }

    public BigDecimal getRequestedPrice() {
        return requestedPrice;
    }

    public void setRequestedPrice(BigDecimal requestedPrice) {
        this.requestedPrice = requestedPrice;
    }

    public BigDecimal getFilledQuantity() {
        return filledQuantity;
    }

    public void setFilledQuantity(BigDecimal filledQuantity) {
        this.filledQuantity = filledQuantity;
    }

    public BigDecimal getAvgFilledPrice() {
        return avgFilledPrice;
    }

    public void setAvgFilledPrice(BigDecimal avgFilledPrice) {
        this.avgFilledPrice = avgFilledPrice;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(OffsetDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public OffsetDateTime getFilledAt() {
        return filledAt;
    }

    public void setFilledAt(OffsetDateTime filledAt) {
        this.filledAt = filledAt;
    }

    public OffsetDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(OffsetDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public JsonNode getBrokerResponseJson() {
        return brokerResponseJson;
    }

    public void setBrokerResponseJson(JsonNode brokerResponseJson) {
        this.brokerResponseJson = brokerResponseJson;
    }

    public JsonNode getPortfolioAfterJson() {
        return portfolioAfterJson;
    }

    public void setPortfolioAfterJson(JsonNode portfolioAfterJson) {
        this.portfolioAfterJson = portfolioAfterJson;
    }

    public BigDecimal getPaperRequestedAmount() {
        return paperRequestedAmount;
    }

    public void setPaperRequestedAmount(BigDecimal paperRequestedAmount) {
        this.paperRequestedAmount = paperRequestedAmount;
    }

    public BigDecimal getPaperSlippageAmount() {
        return paperSlippageAmount;
    }

    public void setPaperSlippageAmount(BigDecimal paperSlippageAmount) {
        this.paperSlippageAmount = paperSlippageAmount;
    }

    public BigDecimal getPaperSellTaxAmount() {
        return paperSellTaxAmount;
    }

    public void setPaperSellTaxAmount(BigDecimal paperSellTaxAmount) {
        this.paperSellTaxAmount = paperSellTaxAmount;
    }

    public BigDecimal getPaperCommissionAmount() {
        return paperCommissionAmount;
    }

    public void setPaperCommissionAmount(BigDecimal paperCommissionAmount) {
        this.paperCommissionAmount = paperCommissionAmount;
    }

    public BigDecimal getPaperActualAmount() {
        return paperActualAmount;
    }

    public void setPaperActualAmount(BigDecimal paperActualAmount) {
        this.paperActualAmount = paperActualAmount;
    }

    public Short getPaperWalkLevels() {
        return paperWalkLevels;
    }

    public void setPaperWalkLevels(Short paperWalkLevels) {
        this.paperWalkLevels = paperWalkLevels;
    }

    public BigDecimal getPaperPartialFillRatio() {
        return paperPartialFillRatio;
    }

    public void setPaperPartialFillRatio(BigDecimal paperPartialFillRatio) {
        this.paperPartialFillRatio = paperPartialFillRatio;
    }

    public JsonNode getPaperOrderbookSnapshotJson() {
        return paperOrderbookSnapshotJson;
    }

    public void setPaperOrderbookSnapshotJson(JsonNode paperOrderbookSnapshotJson) {
        this.paperOrderbookSnapshotJson = paperOrderbookSnapshotJson;
    }

    public BigDecimal getUnfilledQuantity() {
        return unfilledQuantity;
    }

    public void setUnfilledQuantity(BigDecimal unfilledQuantity) {
        this.unfilledQuantity = unfilledQuantity;
    }
}
