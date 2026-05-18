package work.jscraft.alt.strategy.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StrategyInstanceRepository extends JpaRepository<StrategyInstanceEntity, UUID> {

    Optional<StrategyInstanceEntity> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select strategyInstance from StrategyInstanceEntity strategyInstance where strategyInstance.id = :id")
    Optional<StrategyInstanceEntity> findByIdForUpdate(UUID id);

    boolean existsByBrokerAccountIdAndExecutionModeAndLifecycleStateAndIdNot(
            UUID brokerAccountId,
            String executionMode,
            String lifecycleState,
            UUID id);

    List<StrategyInstanceEntity> findByLifecycleStateAndAutoPausedReasonIsNull(String lifecycleState);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StrategyInstanceEntity strategyInstance
            set strategyInstance.scheduleDirty = true
            where strategyInstance.strategyTemplate.id = :strategyTemplateId
              and strategyInstance.cycleMinutes is null
            """)
    int markScheduleDirtyByStrategyTemplateIdAndCycleMinutesIsNull(UUID strategyTemplateId);
}
