package work.jscraft.alt.trading.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaperTradeMatchRepository extends JpaRepository<PaperTradeMatchEntity, UUID> {

    /**
     * 특정 BUY trade_order 의 이미 매칭된 누적 수량 — 잔량 계산에 사용.
     * 매칭 이력 없으면 0 반환.
     */
    @Query("""
            SELECT COALESCE(SUM(m.matchedQuantity), 0)
            FROM PaperTradeMatchEntity m
            WHERE m.buyTradeOrder.id = :buyOrderId
            """)
    BigDecimal sumMatchedQuantityByBuyOrderId(@Param("buyOrderId") UUID buyOrderId);

    /**
     * 직전 N 건 paper_trade_match — SetupMetricService 의 lookback 집계용.
     * exit_time DESC.
     */
    List<PaperTradeMatchEntity> findByStrategyInstance_IdOrderByExitTimeDesc(
            UUID strategyInstanceId, Limit limit);

    /**
     * 지정 기간 paper_trade_match — series 시계열용.
     * exit_time ASC.
     */
    List<PaperTradeMatchEntity> findByStrategyInstance_IdAndExitTimeBetweenOrderByExitTimeAsc(
            UUID strategyInstanceId,
            OffsetDateTime exitFrom,
            OffsetDateTime exitTo);
}
