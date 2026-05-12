package work.jscraft.alt.strategy.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyTemplateRepository extends JpaRepository<StrategyTemplateEntity, UUID> {

    Optional<StrategyTemplateEntity> findByName(String name);
}
