package work.jscraft.alt.trading.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TradeOrderRepository
        extends JpaRepository<TradeOrderEntity, UUID>, JpaSpecificationExecutor<TradeOrderEntity> {

    List<TradeOrderEntity> findByStrategyInstance_IdOrderByRequestedAtDesc(UUID strategyInstanceId, Limit limit);

    List<TradeOrderEntity> findByTradeOrderIntent_TradeDecisionLog_IdOrderByRequestedAtAsc(UUID tradeDecisionLogId);

    List<TradeOrderEntity> findByTradeOrderIntent_SymbolCodeAndRequestedAtBetweenOrderByRequestedAtAsc(
            String symbolCode,
            OffsetDateTime requestedAtFrom,
            OffsetDateTime requestedAtTo);

    List<TradeOrderEntity> findByTradeOrderIntent_SymbolCodeAndStrategyInstance_IdAndRequestedAtBetweenOrderByRequestedAtAsc(
            String symbolCode,
            UUID strategyInstanceId,
            OffsetDateTime requestedAtFrom,
            OffsetDateTime requestedAtTo);

    List<TradeOrderEntity> findByStrategyInstance_IdAndExecutionModeAndOrderStatusIn(
            UUID strategyInstanceId,
            String executionMode,
            java.util.Collection<String> orderStatuses);
}
