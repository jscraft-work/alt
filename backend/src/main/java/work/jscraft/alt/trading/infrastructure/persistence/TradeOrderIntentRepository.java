package work.jscraft.alt.trading.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderIntentRepository extends JpaRepository<TradeOrderIntentEntity, UUID> {

    List<TradeOrderIntentEntity> findByTradeDecisionLog_IdOrderBySequenceNoAsc(UUID tradeDecisionLogId);
}
