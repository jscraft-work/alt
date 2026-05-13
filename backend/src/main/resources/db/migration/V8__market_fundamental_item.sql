-- 펀더멘털 스냅샷 (KIS inquire-price 응답에서 시고저종/거래량을 뺀 나머지 필드).
-- 운영 수집은 일 1회 (장 마감 후). alt-fast.market_snapshots 컬럼명을 그대로 따른다 (KIS 응답 키 매핑).

CREATE TABLE market_fundamental_item (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    symbol_code varchar(40) NOT NULL,
    symbol_name varchar(200) NULL,
    snapshot_at timestamptz NOT NULL,
    business_date date NOT NULL,
    source_name varchar(40) NOT NULL,
    published_at timestamptz NULL,
    external_id varchar(128) NULL,

    -- 핵심 펀더멘털
    per numeric(20,6) NULL,
    pbr numeric(20,6) NULL,
    eps numeric(20,6) NULL,
    bps numeric(20,6) NULL,

    -- 회계 결산월/상장주식수/시가총액 (KIS 원본 키 그대로)
    stac_month varchar(64) NULL,
    lstn_stcn varchar(128) NULL,
    hts_avls bigint NULL,
    cpfn numeric(20,6) NULL,
    stck_fcam bigint NULL,

    -- 52주 고/저
    w52_hgpr integer NULL,
    w52_hgpr_date date NULL,
    w52_hgpr_vrss_prpr_ctrt numeric(20,6) NULL,
    w52_lwpr integer NULL,
    w52_lwpr_date date NULL,
    w52_lwpr_vrss_prpr_ctrt numeric(20,6) NULL,

    -- 250일 고/저
    d250_hgpr integer NULL,
    d250_hgpr_date date NULL,
    d250_hgpr_vrss_prpr_rate numeric(20,6) NULL,
    d250_lwpr integer NULL,
    d250_lwpr_date date NULL,
    d250_lwpr_vrss_prpr_rate numeric(20,6) NULL,

    -- 당해년도 고/저
    stck_dryy_hgpr integer NULL,
    dryy_hgpr_date date NULL,
    dryy_hgpr_vrss_prpr_rate numeric(20,6) NULL,
    stck_dryy_lwpr integer NULL,
    dryy_lwpr_date date NULL,
    dryy_lwpr_vrss_prpr_rate numeric(20,6) NULL,

    -- 외국인/기관/프로그램
    hts_frgn_ehrt numeric(20,6) NULL,
    frgn_hldn_qty bigint NULL,
    frgn_ntby_qty bigint NULL,
    pgtr_ntby_qty bigint NULL,
    vol_tnrt numeric(20,6) NULL,

    -- 신용/대용
    whol_loan_rmnd_rate numeric(20,6) NULL,
    marg_rate numeric(20,6) NULL,
    crdt_able_yn varchar(4) NULL,
    ssts_yn varchar(4) NULL,

    -- 종목 상태 코드
    iscd_stat_cls_code varchar(8) NULL,
    mrkt_warn_cls_code varchar(8) NULL,
    invt_caful_yn varchar(4) NULL,
    short_over_yn varchar(4) NULL,
    sltr_yn varchar(4) NULL,
    mang_issu_cls_code varchar(8) NULL,
    temp_stop_yn varchar(4) NULL,
    oprc_rang_cont_yn varchar(4) NULL,
    clpr_rang_cont_yn varchar(4) NULL,
    grmn_rate_cls_code varchar(8) NULL,
    new_hgpr_lwpr_cls_code varchar(8) NULL,
    rprs_mrkt_kor_name varchar(60) NULL,
    bstp_kor_isnm varchar(80) NULL,
    vi_cls_code varchar(8) NULL,
    ovtm_vi_cls_code varchar(8) NULL,
    last_ssts_cntg_qty bigint NULL,
    apprch_rate numeric(20,6) NULL,

    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_market_fundamental_item PRIMARY KEY (id, business_date)
) PARTITION BY RANGE (business_date);

CREATE INDEX idx_market_fundamental_item_symbol_snapshot_at
    ON market_fundamental_item (symbol_code, snapshot_at DESC);

CREATE INDEX idx_market_fundamental_item_business_date
    ON market_fundamental_item (business_date);

CREATE UNIQUE INDEX uq_market_fundamental_item_symbol_date_source
    ON market_fundamental_item (symbol_code, business_date, source_name);

CREATE TABLE market_fundamental_item_default
    PARTITION OF market_fundamental_item DEFAULT;
