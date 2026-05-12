package work.jscraft.alt.strategy.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyInstancePromptVersionRepository
        extends JpaRepository<StrategyInstancePromptVersionEntity, UUID> {

    List<StrategyInstancePromptVersionEntity> findByStrategyInstanceIdOrderByVersionNoAsc(UUID strategyInstanceId);

    Optional<StrategyInstancePromptVersionEntity> findByIdAndStrategyInstanceId(UUID id, UUID strategyInstanceId);

    Optional<StrategyInstancePromptVersionEntity> findTopByStrategyInstanceIdOrderByVersionNoDesc(UUID strategyInstanceId);
}
