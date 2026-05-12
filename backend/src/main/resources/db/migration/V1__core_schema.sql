CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE llm_model_profile (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    purpose varchar(40) NOT NULL,
    provider varchar(40) NOT NULL,
    model_name varchar(120) NOT NULL,
    command varchar(200) NOT NULL,
    args_json jsonb NOT NULL,
    default_timeout_seconds integer NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz NULL,
    CONSTRAINT chk_llm_model_profile_purpose
        CHECK (purpose IN ('trading_decision', 'daily_report', 'news_assessment'))
);

CREATE INDEX idx_llm_model_profile_purpose_enabled
    ON llm_model_profile (purpose, enabled);

CREATE TABLE strategy_template (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(120) NOT NULL,
    description text NOT NULL,
    default_cycle_minutes integer NOT NULL,
    default_prompt_text text NOT NULL,
    default_input_spec_json jsonb NOT NULL,
    default_execution_config_json jsonb NOT NULL,
    default_trading_model_profile_id uuid NOT NULL REFERENCES llm_model_profile (id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz NULL,
    CONSTRAINT chk_strategy_template_cycle_minutes
        CHECK (default_cycle_minutes BETWEEN 1 AND 30)
);

CREATE TABLE system_parameter (
    parameter_key varchar(120) PRIMARY KEY,
    value_json jsonb NOT NULL,
    description text NULL,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE broker_account (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    broker_code varchar(40) NOT NULL,
    broker_account_no varchar(120) NOT NULL,
    account_alias varchar(120) NULL,
    account_masked varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz NULL,
    CONSTRAINT uq_broker_account_broker_code_account_no
        UNIQUE (broker_code, broker_account_no)
);

CREATE TABLE asset_master (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol_code varchar(40) NOT NULL,
    symbol_name varchar(200) NOT NULL,
    market_type varchar(40) NOT NULL,
    dart_corp_code varchar(20) NULL,
    is_hidden boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz NULL,
    CONSTRAINT uq_asset_master_symbol_code
        UNIQUE (symbol_code)
);

CREATE TABLE app_user (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    login_id varchar(120) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name varchar(120) NOT NULL,
    role_code varchar(40) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    last_login_at timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz NULL,
    CONSTRAINT uq_app_user_login_id
        UNIQUE (login_id)
);
