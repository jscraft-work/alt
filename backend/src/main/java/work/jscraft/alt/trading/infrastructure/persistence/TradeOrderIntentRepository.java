package work.jscraft.alt.trading.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeOrderIntentRepository extends JpaRepository<TradeOrderIntentEntity, UUID> {

    List<TradeOrderIntentEntity> findByTradeDecisionLog_IdOrderBySequenceNoAsc(UUID tradeDecisionLogId);

    @Query("""
            SELECT i FROM TradeOrderIntentEntity i
             WHERE i.tradeDecisionLog.strategyInstance.id = :strategyInstanceId
               AND i.symbolCode = :symbolCode
               AND i.tradeDecisionLog.cycleStartedAt >= :since
             ORDER BY i.tradeDecisionLog.cycleStartedAt DESC
            """)
    List<TradeOrderIntentEntity> findRecentByStrategyAndSymbol(
            @Param("strategyInstanceId") UUID strategyInstanceId,
            @Param("symbolCode") String symbolCode,
            @Param("since") OffsetDateTime since);
}
