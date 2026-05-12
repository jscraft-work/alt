package work.jscraft.alt.common.persistence;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class SoftDeletableUuidEntity extends UuidBaseEntity {

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
