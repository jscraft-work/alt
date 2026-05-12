package work.jscraft.alt.strategy.infrastructure.persistence;

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
@Table(name = "strategy_template")
@SQLDelete(sql = """
        UPDATE strategy_template
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = ? AND version = ?
        """)
@SQLRestriction("deleted_at is null")
public class StrategyTemplateEntity extends SoftDeletableUuidEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "default_cycle_minutes", nullable = false)
    private int defaultCycleMinutes;

    @Column(name = "default_prompt_text", nullable = false, columnDefinition = "text")
    private String defaultPromptText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_input_spec_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode defaultInputSpecJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_execution_config_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode defaultExecutionConfigJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "default_trading_model_profile_id", nullable = false)
    private LlmModelProfileEntity defaultTradingModelProfile;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDefaultCycleMinutes() {
        return defaultCycleMinutes;
    }

    public void setDefaultCycleMinutes(int defaultCycleMinutes) {
        this.defaultCycleMinutes = defaultCycleMinutes;
    }

    public String getDefaultPromptText() {
        return defaultPromptText;
    }

    public void setDefaultPromptText(String defaultPromptText) {
        this.defaultPromptText = defaultPromptText;
    }

    public JsonNode getDefaultInputSpecJson() {
        return defaultInputSpecJson;
    }

    public void setDefaultInputSpecJson(JsonNode defaultInputSpecJson) {
        this.defaultInputSpecJson = defaultInputSpecJson;
    }

    public JsonNode getDefaultExecutionConfigJson() {
        return defaultExecutionConfigJson;
    }

    public void setDefaultExecutionConfigJson(JsonNode defaultExecutionConfigJson) {
        this.defaultExecutionConfigJson = defaultExecutionConfigJson;
    }

    public LlmModelProfileEntity getDefaultTradingModelProfile() {
        return defaultTradingModelProfile;
    }

    public void setDefaultTradingModelProfile(LlmModelProfileEntity defaultTradingModelProfile) {
        this.defaultTradingModelProfile = defaultTradingModelProfile;
    }
}
