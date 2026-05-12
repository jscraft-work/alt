package work.jscraft.alt.trading.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TradeDecisionLogRepository
        extends JpaRepository<TradeDecisionLogEntity, UUID>, JpaSpecificationExecutor<TradeDecisionLogEntity> {

    Optional<TradeDecisionLogEntity> findFirstByStrategyInstance_IdOrderByCycleStartedAtDesc(UUID strategyInstanceId);
}
