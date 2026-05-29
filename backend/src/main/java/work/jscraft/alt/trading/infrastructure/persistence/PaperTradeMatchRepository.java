package work.jscraft.alt.trading.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.UUID;

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
}
