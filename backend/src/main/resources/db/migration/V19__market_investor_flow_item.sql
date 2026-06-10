-- KIS inquire-investor (FHKST01010900) 응답의 종목별 일별 투자자 순매수 (개인/외국인/기관계).
-- 한 행 = 한 종목 × 하루. 수량은 주, 거래대금(*_ntby_amt)은 백만원. 운영 수집은 일 1회 (장 마감 후).

CREATE TABLE market_investor_flow_item (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    symbol_code varchar(40) NOT NULL,
    trade_date date NOT NULL,
    source_name varchar(40) NOT NULL,
    close_price integer NULL,
    indv_ntby_qty bigint NULL,
    frgn_ntby_qty bigint NULL,
    orgn_ntby_qty bigint NULL,
    indv_ntby_amt bigint NULL,
    frgn_ntby_amt bigint NULL,
    orgn_ntby_amt bigint NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_investor_flow_item PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_market_investor_flow_item_symbol_date_source
    ON market_investor_flow_item (symbol_code, trade_date, source_name);

CREATE INDEX idx_market_investor_flow_item_symbol_trade_date
    ON market_investor_flow_item (symbol_code, trade_date DESC);
