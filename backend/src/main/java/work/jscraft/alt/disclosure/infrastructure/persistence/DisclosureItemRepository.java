package work.jscraft.alt.disclosure.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DisclosureItemRepository
        extends JpaRepository<DisclosureItemEntity, UUID>, JpaSpecificationExecutor<DisclosureItemEntity> {

    boolean existsByDisclosureNo(String disclosureNo);
}
