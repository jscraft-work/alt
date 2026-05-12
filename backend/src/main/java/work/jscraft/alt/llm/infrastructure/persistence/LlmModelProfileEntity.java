package work.jscraft.alt.llm.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import work.jscraft.alt.common.persistence.SoftDeletableUuidEntity;

@Entity
@Table(name = "llm_model_profile")
@SQLDelete(sql = """
        UPDATE llm_model_profile
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = ? AND version = ?
        """)
@SQLRestriction("deleted_at is null")
public class LlmModelProfileEntity extends SoftDeletableUuidEntity {

    @Column(nullable = false, length = 40)
    private String purpose;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;

    @Column(nullable = false)
    private boolean enabled = true;

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
