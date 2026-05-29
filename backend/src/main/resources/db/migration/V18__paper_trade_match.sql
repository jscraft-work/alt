-- V18__paper_trade_match.sql
-- BUY/SELL 매칭 raw row 보관 — 통과 기준 (#3 PF, #4 샤프, #6 최악 5 일) 계산의 source.
-- SELL 체결 직후 PaperTradeMatcher (F5) 가 FIFO 매칭으로 row INSERT.
-- 같은 종목 다중 BUY 후 부분 SELL 시 multi row (1 SELL → N BUY 매칭).
--
-- gross_pnl_pct = (sell.requested_price - buy.requested_price) / buy.requested_price
--   - 의도가가 그대로 체결됐다면 이상적 PnL
-- net_pnl_pct  = (sell.paper_actual_amount - buy.paper_actual_amount) / buy.paper_actual_amount
--   - 실제 현금 흐름 기반. cost wall (슬리피지 + 매도세 + 수수료) 차감 후
-- gross - net = cost wall 실측 (예상 0.78% 근방)

CREATE TABLE paper_trade_match (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_instance_id uuid NOT NULL REFERENCES strategy_instance(id),
    buy_trade_order_id uuid NOT NULL REFERENCES trade_order(id),
    sell_trade_order_id uuid NOT NULL REFERENCES trade_order(id),
    symbol_code varchar(40) NOT NULL,
    matched_quantity numeric(19,8) NOT NULL,            -- 이 매칭에 사용된 수량 (BUY 잔량 ≤ SELL 잔량)
    entry_time timestamptz NOT NULL,                    -- buy.filled_at
    exit_time timestamptz NOT NULL,                     -- sell.filled_at
    holding_minutes integer NOT NULL,                   -- exit - entry (분 단위)
    gross_pnl_pct numeric(10,6) NOT NULL,
    net_pnl_pct numeric(10,6) NOT NULL,
    slippage_buy_pct numeric(10,6),                     -- buy.paper_slippage_amount / buy.paper_requested_amount
    slippage_sell_pct numeric(10,6),                    -- sell.paper_slippage_amount / sell.paper_requested_amount
    sell_tax_pct numeric(10,6),                         -- sell.paper_sell_tax_amount / sell.paper_requested_amount
    fee_pct numeric(10,6),                              -- (buy.paper_commission_amount + sell.paper_commission_amount) / buy.paper_requested_amount
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 직전 N 건 조회 (SetupMetricService 의 핵심 쿼리)
CREATE INDEX idx_paper_trade_match_instance_exit
    ON paper_trade_match(strategy_instance_id, exit_time DESC);

-- BUY/SELL 추적 (디버깅, multi-row 매칭 확인)
CREATE INDEX idx_paper_trade_match_buy
    ON paper_trade_match(buy_trade_order_id);

CREATE INDEX idx_paper_trade_match_sell
    ON paper_trade_match(sell_trade_order_id);
