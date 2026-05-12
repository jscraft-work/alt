package work.jscraft.alt.strategy.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyInstanceWatchlistRelationRepository
        extends JpaRepository<StrategyInstanceWatchlistRelationEntity, UUID> {

    List<StrategyInstanceWatchlistRelationEntity> findByStrategyInstanceId(UUID strategyInstanceId);

    List<StrategyInstanceWatchlistRelationEntity> findByStrategyInstanceIdOrderByCreatedAtAsc(UUID strategyInstanceId);

    Optional<StrategyInstanceWatchlistRelationEntity> findByStrategyInstanceIdAndAssetMasterId(
            UUID strategyInstanceId,
            UUID assetMasterId);

    boolean existsByStrategyInstanceId(UUID strategyInstanceId);

    boolean existsByStrategyInstanceIdAndAssetMasterId(UUID strategyInstanceId, UUID assetMasterId);

    long countByStrategyInstanceId(UUID strategyInstanceId);
}
