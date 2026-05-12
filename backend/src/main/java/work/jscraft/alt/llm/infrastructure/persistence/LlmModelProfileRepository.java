package work.jscraft.alt.llm.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmModelProfileRepository extends JpaRepository<LlmModelProfileEntity, UUID> {

    List<LlmModelProfileEntity> findByPurposeAndEnabledTrue(String purpose);
}
