CREATE TABLE market_daily_item (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    symbol_code varchar(40) NOT NULL,
    business_date date NOT NULL,
    open_price numeric(19,8) NOT NULL,
    high_price numeric(19,8) NOT NULL,
    low_price numeric(19,8) NOT NULL,
    close_price numeric(19,8) NOT NULL,
    volume numeric(19,4) NULL,
    source_name varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_daily_item PRIMARY KEY (id, business_date),
    CONSTRAINT uq_market_daily_item_symbol_business_date_source
        UNIQUE (symbol_code, business_date, source_name)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_market_daily_item_symbol_business_date
    ON market_daily_item (symbol_code, business_date DESC);

CREATE INDEX idx_market_daily_item_business_date
    ON market_daily_item (business_date);

CREATE TABLE market_daily_item_default PARTITION OF market_daily_item DEFAULT;

INSERT INTO market_daily_item (
    symbol_code, business_date,
    open_price, high_price, low_price, close_price,
    volume, source_name
)
SELECT
    symbol_code,
    business_date,
    (array_agg(open_price ORDER BY bar_time))[1] AS open_price,
    MAX(high_price) AS high_price,
    MIN(low_price) AS low_price,
    (array_agg(close_price ORDER BY bar_time DESC))[1] AS close_price,
    SUM(volume) AS volume,
    'aggregated:market_minute_item' AS source_name
FROM market_minute_item
GROUP BY symbol_code, business_date
ON CONFLICT (symbol_code, business_date, source_name) DO NOTHING;
