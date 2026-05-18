package work.jscraft.alt.strategy.infrastructure.persistence;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import work.jscraft.alt.common.persistence.SoftDeletableUuidEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;

@Entity
@Table(name = "strategy_instance")
@SQLDelete(sql = """
        UPDATE strategy_instance
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = ? AND version = ?
        """)
@SQLRestriction("deleted_at is null")
public class StrategyInstanceEntity extends SoftDeletableUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_template_id", nullable = false)
    private StrategyTemplateEntity strategyTemplate;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "lifecycle_state", nullable = false, length = 20)
    private String lifecycleState;

    @Column(name = "execution_mode", nullable = false, length = 20)
    private String executionMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broker_account_id")
    private BrokerAccountEntity brokerAccount;

    @Column(name = "budget_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal budgetAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_prompt_version_id")
    private StrategyInstancePromptVersionEntity currentPromptVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trading_model_profile_id")
    private LlmModelProfileEntity tradingModelProfile;

    @Column(name = "cycle_minutes")
    private Integer cycleMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_config_override_json", columnDefinition = "jsonb")
    private JsonNode executionConfigOverrideJson;

    @Column(name = "auto_paused_reason", length = 40)
    private String autoPausedReason;

    @Column(name = "auto_paused_at")
    private java.time.OffsetDateTime autoPausedAt;

    public StrategyTemplateEntity getStrategyTemplate() {
        return strategyTemplate;
    }

    public void setStrategyTemplate(StrategyTemplateEntity strategyTemplate) {
        this.strategyTemplate = strategyTemplate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(String lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public BrokerAccountEntity getBrokerAccount() {
        return brokerAccount;
    }

    public void setBrokerAccount(BrokerAccountEntity brokerAccount) {
        this.brokerAccount = brokerAccount;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public StrategyInstancePromptVersionEntity getCurrentPromptVersion() {
        return currentPromptVersion;
    }

    public void setCurrentPromptVersion(StrategyInstancePromptVersionEntity currentPromptVersion) {
        this.currentPromptVersion = currentPromptVersion;
    }

    public LlmModelProfileEntity getTradingModelProfile() {
        return tradingModelProfile;
    }

    public void setTradingModelProfile(LlmModelProfileEntity tradingModelProfile) {
        this.tradingModelProfile = tradingModelProfile;
    }

    public Integer getCycleMinutes() {
        return cycleMinutes;
    }

    public void setCycleMinutes(Integer cycleMinutes) {
        this.cycleMinutes = cycleMinutes;
    }

    public JsonNode getExecutionConfigOverrideJson() {
        return executionConfigOverrideJson;
    }

    public void setExecutionConfigOverrideJson(JsonNode executionConfigOverrideJson) {
        this.executionConfigOverrideJson = executionConfigOverrideJson;
    }

    public String getAutoPausedReason() {
        return autoPausedReason;
    }

    public void setAutoPausedReason(String autoPausedReason) {
        this.autoPausedReason = autoPausedReason;
    }

    public java.time.OffsetDateTime getAutoPausedAt() {
        return autoPausedAt;
    }

    public void setAutoPausedAt(java.time.OffsetDateTime autoPausedAt) {
        this.autoPausedAt = autoPausedAt;
    }
}
