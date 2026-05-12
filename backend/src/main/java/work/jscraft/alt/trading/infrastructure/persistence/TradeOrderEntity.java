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
}
