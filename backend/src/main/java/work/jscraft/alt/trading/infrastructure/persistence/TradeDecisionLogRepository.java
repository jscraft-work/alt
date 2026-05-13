package work.jscraft.alt.trading.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeDecisionLogRepository
        extends JpaRepository<TradeDecisionLogEntity, UUID>, JpaSpecificationExecutor<TradeDecisionLogEntity> {

    Optional<TradeDecisionLogEntity> findFirstByStrategyInstance_IdOrderByCycleStartedAtDesc(UUID strategyInstanceId);

    @Query("""
            SELECT d FROM TradeDecisionLogEntity d
             WHERE d.strategyInstance.id = :strategyInstanceId
               AND d.cycleStartedAt >= :since
             ORDER BY d.cycleStartedAt DESC
            """)
    List<TradeDecisionLogEntity> findRecentByStrategy(
            @Param("strategyInstanceId") UUID strategyInstanceId,
            @Param("since") OffsetDateTime since);
}
