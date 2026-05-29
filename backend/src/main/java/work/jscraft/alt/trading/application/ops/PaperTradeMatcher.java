package work.jscraft.alt.trading.application.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;

/**
 * SELL paper 체결 시 FIFO 매칭으로 {@code paper_trade_match} row INSERT.
 *
 * <p>Day 3 시점: PaperOrderExecutor 가 SELL 체결 직후 호출하는 hook 만 stub. Day 4 에 실 FIFO 매칭
 * + multi-row + cost breakdown 컬럼 계산 구현.
 *
 * <ul>
 *   <li>같은 (strategy_instance_id, symbol_code, side=BUY, execution_mode=paper) 의 BUY 들 중 잔여
 *       (filled_quantity − sum(paper_trade_match.matched_quantity)) &gt; 0 인 BUY 를 requested_at ASC 로 매칭</li>
 *   <li>SELL 의 filled_quantity 다 채울 때까지 multi-row 가능 — 부분 매칭 OK</li>
 *   <li>net_pnl_pct = (sell.paper_actual_amount × matched_ratio − buy.paper_actual_amount × matched_ratio)
 *       / buy.paper_actual_amount 의 매칭분 환산</li>
 * </ul>
 */
@Service
public class PaperTradeMatcher {

    private static final Logger log = LoggerFactory.getLogger(PaperTradeMatcher.class);

    public PaperTradeMatcher() {
    }

    /**
     * SELL paper 체결 직후 PaperOrderExecutor 가 호출.
     *
     * <p>Day 3 stub — Day 4 에 FIFO 매칭 + paper_trade_match INSERT 구현.
     */
    public void onSellFilled(TradeOrderEntity sellOrder) {
        // TODO(Day 4): FIFO 매칭 + paper_trade_match INSERT
        log.debug("paper_trade_match stub — sell order {} (Day 4 에 실 구현)", sellOrder.getId());
    }
}
