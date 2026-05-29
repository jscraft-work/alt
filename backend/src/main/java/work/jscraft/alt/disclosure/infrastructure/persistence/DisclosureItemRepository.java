package work.jscraft.alt.disclosure.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DisclosureItemRepository
        extends JpaRepository<DisclosureItemEntity, UUID>, JpaSpecificationExecutor<DisclosureItemEntity> {

    boolean existsByDisclosureNo(String disclosureNo);

    @Query("SELECT MAX(d.publishedAt) FROM DisclosureItemEntity d")
    Optional<OffsetDateTime> findMaxPublishedAt();
}
