package work.jscraft.alt.macro.infrastructure.persistence;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;

@Entity
@Table(name = "macro_item")
public class MacroItemEntity extends CreatedAtOnlyUuidEntity {

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode payloadJson;

    public LocalDate getBaseDate() {
        return baseDate;
    }

    public void setBaseDate(LocalDate baseDate) {
        this.baseDate = baseDate;
    }

    public JsonNode getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(JsonNode payloadJson) {
        this.payloadJson = payloadJson;
    }
}
