package work.jscraft.alt.trading.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * trade-history 페이지네이션 + 필터 검색 (F3).
     *
     * <p>모든 파라미터는 비 null 센티넬을 전달 — null/optional 분기는 서비스 레이어가 책임.
     * (PostgreSQL JPQL 의 {@code :param IS NULL} 패턴은 prepared statement 의 타입 추론 실패로 사용 불가)
     *
     * <ul>
     *   <li>{@code from}: 비활성화 시 매우 이른 시각 (예: 1900-01-01)</li>
     *   <li>{@code to}: 비활성화 시 매우 먼 미래 (예: 9999-12-31)</li>
     *   <li>{@code symbol}: 비활성화 시 빈 문자열 — JPQL 이 {@code ('' = :symbol OR m.symbolCode = :symbol)} 로 분기</li>
     *   <li>{@code winOnly} / {@code lossOnly}: boolean — false 면 해당 필터 무효</li>
     * </ul>
     *
     * <p>{@code JOIN FETCH} 로 buy/sell trade_order 의 V17 컬럼을 함께 로드해 N+1 회피.
     */
    @Query(value = """
            SELECT m FROM PaperTradeMatchEntity m
            JOIN FETCH m.buyTradeOrder
            JOIN FETCH m.sellTradeOrder
            WHERE m.strategyInstance.id = :instanceId
              AND m.exitTime >= :from
              AND m.exitTime < :to
              AND ('' = :symbol OR m.symbolCode = :symbol)
              AND (:winOnly = FALSE OR m.netPnlPct > 0)
              AND (:lossOnly = FALSE OR m.netPnlPct < 0)
            """,
           countQuery = """
            SELECT COUNT(m) FROM PaperTradeMatchEntity m
            WHERE m.strategyInstance.id = :instanceId
              AND m.exitTime >= :from
              AND m.exitTime < :to
              AND ('' = :symbol OR m.symbolCode = :symbol)
              AND (:winOnly = FALSE OR m.netPnlPct > 0)
              AND (:lossOnly = FALSE OR m.netPnlPct < 0)
            """)
    Page<PaperTradeMatchEntity> findTradeHistory(
            @Param("instanceId") UUID instanceId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("symbol") String symbol,
            @Param("winOnly") boolean winOnly,
            @Param("lossOnly") boolean lossOnly,
            Pageable pageable);

    /**
     * trade-history 필터 결과의 글로벌 summary — 페이지 0 응답 시 한 번만 계산.
     */
    @Query("""
            SELECT new work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchRepository$TradeHistorySummary(
                COUNT(m),
                COALESCE(SUM(CASE WHEN m.netPnlPct > 0 THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(m.netPnlPct), 0)
            )
            FROM PaperTradeMatchEntity m
            WHERE m.strategyInstance.id = :instanceId
              AND m.exitTime >= :from
              AND m.exitTime < :to
              AND ('' = :symbol OR m.symbolCode = :symbol)
              AND (:winOnly = FALSE OR m.netPnlPct > 0)
              AND (:lossOnly = FALSE OR m.netPnlPct < 0)
            """)
    Optional<TradeHistorySummary> summarizeTradeHistory(
            @Param("instanceId") UUID instanceId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("symbol") String symbol,
            @Param("winOnly") boolean winOnly,
            @Param("lossOnly") boolean lossOnly);

    /** JPQL constructor expression projection target. */
    record TradeHistorySummary(long tradesCount, long winCount, BigDecimal sumNetPnlPct) {
    }
}
