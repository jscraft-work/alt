-- V17__paper_order_cost_breakdown.sql
-- 운영자 요청: paper trade row 한 줄로 "원래 X 원 / 슬리피지 a 원 / 세금 b 원 / 수수료 c 원 / 실제 d 원" 가
-- 명시적으로 보이는 amount 기반 비용 breakdown 컬럼. 호가창 walk 결과 (walk_levels, partial_fill_ratio,
-- orderbook_snapshot_json, unfilled_quantity) 도 같이 적재.
--
-- invariant (PaperOrderExecutor 에서 enforce):
--   BUY:  paper_actual_amount = paper_requested_amount + paper_slippage_amount + paper_commission_amount
--   SELL: paper_actual_amount = paper_requested_amount - paper_slippage_amount
--                              - paper_sell_tax_amount - paper_commission_amount
--   portfolio.cash 변동 절대값 = paper_actual_amount (불일치 시 IllegalStateException)
--
-- 모든 금액 컬럼은 KRW 단위 + numeric(19,4) (원 단위 소수 4 자리 — 세금/수수료 round 여유).

ALTER TABLE trade_order
    -- 비용 breakdown 5 컬럼 (운영자 요청 amount 기반)
    ADD COLUMN paper_requested_amount numeric(19,4),    -- requested_price × filled_quantity. MARKET 은 walk 시작 직전 1 단계 호가 기준 (PaperOrderExecutor 가 채움)
    ADD COLUMN paper_slippage_amount numeric(19,4),     -- |avg_filled_price - requested_price| × filled_quantity. 항상 0 이상
    ADD COLUMN paper_sell_tax_amount numeric(19,4),     -- SELL 만 양수, BUY 는 0. 0.15% × (avg_filled_price × filled_quantity). KOSPI 농특세 / KOSDAQ 거래세 (asset_master.market_type 분기, 율 동일)
    ADD COLUMN paper_commission_amount numeric(19,4),   -- 양쪽 다. 0.014% × (avg_filled_price × filled_quantity). 한투 비대면 기준 (운영자 약정 확인 후 상수 변경)
    ADD COLUMN paper_actual_amount numeric(19,4),       -- portfolio.cash 변동 절대값. invariant 검증 대상.

    -- 호가 walk 관련 4 컬럼
    ADD COLUMN paper_walk_levels smallint,              -- 호가 walk 가 사용한 단계 (1~5). 5 미만이면 그 단계에서 완전체결.
    ADD COLUMN paper_partial_fill_ratio numeric(6,4),   -- filled_quantity / requested_quantity. 1.0 = 완전체결, < 1.0 = 부분체결
    ADD COLUMN paper_orderbook_snapshot_json jsonb,     -- walk 시점 호가 5 단계 + walk path 기록
    ADD COLUMN unfilled_quantity numeric(19,8);         -- 미체결 잔량 (= requested_quantity - filled_quantity). 0 = 완전체결.
