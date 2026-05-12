package work.jscraft.alt.strategy.infrastructure.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;

@Entity
@Table(name = "strategy_instance_prompt_version")
public class StrategyInstancePromptVersionEntity extends CreatedAtOnlyUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_instance_id", nullable = false)
    private StrategyInstanceEntity strategyInstance;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "prompt_text", nullable = false, columnDefinition = "text")
    private String promptText;

    @Column(name = "change_note", columnDefinition = "text")
    private String changeNote;

    @Column(name = "created_by")
    private UUID createdBy;

    public StrategyInstanceEntity getStrategyInstance() {
        return strategyInstance;
    }

    public void setStrategyInstance(StrategyInstanceEntity strategyInstance) {
        this.strategyInstance = strategyInstance;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String changeNote) {
        this.changeNote = changeNote;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
