CREATE TABLE market_price_item (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    symbol_code varchar(40) NOT NULL,
    snapshot_at timestamptz NOT NULL,
    business_date date NOT NULL,
    last_price numeric(19,8) NOT NULL,
    open_price numeric(19,8) NULL,
    high_price numeric(19,8) NULL,
    low_price numeric(19,8) NULL,
    volume numeric(19,4) NULL,
    source_name varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_price_item PRIMARY KEY (id, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_market_price_item_symbol_snapshot_at
    ON market_price_item (symbol_code, snapshot_at DESC);

CREATE INDEX idx_market_price_item_business_date
    ON market_price_item (business_date);

CREATE TABLE market_price_item_default PARTITION OF market_price_item DEFAULT;

CREATE TABLE market_minute_item (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    symbol_code varchar(40) NOT NULL,
    bar_time timestamptz NOT NULL,
    business_date date NOT NULL,
    open_price numeric(19,8) NOT NULL,
    high_price numeric(19,8) NOT NULL,
    low_price numeric(19,8) NOT NULL,
    close_price numeric(19,8) NOT NULL,
    volume numeric(19,4) NULL,
    source_name varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_minute_item PRIMARY KEY (id, business_date),
    CONSTRAINT uq_market_minute_item_symbol_bar_time_source
        UNIQUE (symbol_code, bar_time, source_name, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_market_minute_item_symbol_bar_time
    ON market_minute_item (symbol_code, bar_time DESC);

CREATE INDEX idx_market_minute_item_business_date
    ON market_minute_item (business_date);

CREATE TABLE market_minute_item_default PARTITION OF market_minute_item DEFAULT;

CREATE TABLE news_item (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    provider_name varchar(40) NOT NULL,
    external_news_id varchar(200) NULL,
    title text NOT NULL,
    article_url text NOT NULL,
    published_at timestamptz NOT NULL,
    summary text NULL,
    usefulness_status varchar(30) NOT NULL,
    usefulness_assessed_at timestamptz NULL,
    usefulness_model_profile_id uuid NULL REFERENCES llm_model_profile (id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_news_item PRIMARY KEY (id, published_at),
    CONSTRAINT chk_news_item_usefulness_status
        CHECK (usefulness_status IN ('useful', 'not_useful', 'unclassified'))
) PARTITION BY RANGE (published_at);

CREATE INDEX idx_news_item_published_at
    ON news_item (published_at DESC);

CREATE INDEX idx_news_item_usefulness_status
    ON news_item (usefulness_status, published_at DESC);

CREATE TABLE news_item_default PARTITION OF news_item DEFAULT;

CREATE TABLE news_asset_relation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    news_item_id uuid NOT NULL,
    asset_master_id uuid NOT NULL REFERENCES asset_master (id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_news_asset_relation_news_asset
        UNIQUE (news_item_id, asset_master_id)
);

CREATE INDEX idx_news_asset_relation_news_item_id
    ON news_asset_relation (news_item_id);

CREATE INDEX idx_news_asset_relation_asset_master_id
    ON news_asset_relation (asset_master_id);

CREATE TABLE disclosure_item (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    dart_corp_code varchar(20) NOT NULL,
    disclosure_no varchar(120) NOT NULL,
    title text NOT NULL,
    published_at timestamptz NOT NULL,
    preview_text text NULL,
    document_url text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_disclosure_item PRIMARY KEY (id, published_at),
    CONSTRAINT uq_disclosure_item_disclosure_no
        UNIQUE (disclosure_no, published_at)
) PARTITION BY RANGE (published_at);

CREATE INDEX idx_disclosure_item_corp_published_at
    ON disclosure_item (dart_corp_code, published_at DESC);

CREATE TABLE disclosure_item_default PARTITION OF disclosure_item DEFAULT;
