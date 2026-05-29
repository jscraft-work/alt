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

    /**
     * V18 paper_trade_match 의 FIFO 매칭 source — 같은 instance + symbol 의 paper BUY 들을
     * requested_at ASC 순으로. PaperTradeMatcher 가 잔량 (filled - sum(matched)) > 0 인 BUY 만
     * 매칭 대상으로 사용.
     */
    List<TradeOrderEntity> findByStrategyInstance_IdAndExecutionModeAndTradeOrderIntent_SideAndTradeOrderIntent_SymbolCodeOrderByRequestedAtAsc(
            UUID strategyInstanceId,
            String executionMode,
            String side,
            String symbolCode);
}
