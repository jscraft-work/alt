package work.jscraft.alt.portfolio.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPositionEntity, UUID> {

    List<PortfolioPositionEntity> findByStrategyInstanceId(UUID strategyInstanceId);

    Optional<PortfolioPositionEntity> findByStrategyInstanceIdAndSymbolCode(UUID strategyInstanceId, String symbolCode);
}
