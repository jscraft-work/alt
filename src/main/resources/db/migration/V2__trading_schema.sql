CREATE TABLE strategy_instance (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_template_id uuid NOT NULL REFERENCES strategy_template (id),
    name varchar(120) NOT NULL,
    lifecycle_state varchar(20) NOT NULL,
    execution_mode varchar(20) NOT NULL,
    broker_account_id uuid NULL REFERENCES broker_account (id),
    budget_amount numeric(19,4) NOT NULL,
    current_prompt_version_id uuid NULL,
    trading_model_profile_id uuid NULL REFERENCES llm_model_profile (id),
    input_spec_override_json jsonb NULL,
    execution_config_override_json jsonb NULL,
    auto_paused_reason varchar(40) NULL,
    auto_paused_at timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz NULL,
    CONSTRAINT chk_strategy_instance_lifecycle_state
        CHECK (lifecycle_state IN ('draft', 'active', 'inactive')),
    CONSTRAINT chk_strategy_instance_execution_mode
        CHECK (execution_mode IN ('paper', 'live')),
    CONSTRAINT chk_strategy_instance_budget_amount
        CHECK (budget_amount > 0),
    CONSTRAINT chk_strategy_instance_auto_paused_reason
        CHECK (auto_paused_reason IN ('reconcile_failed') OR auto_paused_reason IS NULL)
);

CREATE TABLE strategy_instance_prompt_version (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_instance_id uuid NOT NULL REFERENCES strategy_instance (id),
    version_no integer NOT NULL,
    prompt_text text NOT NULL,
    change_note text NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NULL,
    CONSTRAINT uq_strategy_instance_prompt_version_instance_version
        UNIQUE (strategy_instance_id, version_no)
);

ALTER TABLE strategy_instance
    ADD CONSTRAINT fk_strategy_instance_current_prompt_version
    FOREIGN KEY (current_prompt_version_id)
    REFERENCES strategy_instance_prompt_version (id);

CREATE INDEX idx_strategy_instance_prompt_version_instance_created_at
    ON strategy_instance_prompt_version (strategy_instance_id, created_at DESC);

CREATE TABLE strategy_instance_watchlist_relation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_instance_id uuid NOT NULL REFERENCES strategy_instance (id),
    asset_master_id uuid NOT NULL REFERENCES asset_master (id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz NULL
);

CREATE UNIQUE INDEX uq_strategy_instance_watchlist_relation_live
    ON strategy_instance_watchlist_relation (strategy_instance_id, asset_master_id)
    WHERE deleted_at IS NULL;

CREATE TABLE portfolio (
    strategy_instance_id uuid PRIMARY KEY REFERENCES strategy_instance (id),
    cash_amount numeric(19,4) NOT NULL,
    total_asset_amount numeric(19,4) NOT NULL,
    realized_pnl_today numeric(19,4) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE portfolio_position (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_instance_id uuid NOT NULL REFERENCES strategy_instance (id),
    symbol_code varchar(40) NOT NULL,
    quantity numeric(19,8) NOT NULL,
    avg_buy_price numeric(19,8) NOT NULL,
    last_mark_price numeric(19,8) NULL,
    unrealized_pnl numeric(19,4) NULL,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_portfolio_position_instance_symbol
        UNIQUE (strategy_instance_id, symbol_code)
);

CREATE TABLE trade_cycle_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_instance_id uuid NOT NULL REFERENCES strategy_instance (id),
    cycle_started_at timestamptz NOT NULL,
    cycle_finished_at timestamptz NULL,
    business_date date NOT NULL,
    cycle_stage varchar(40) NOT NULL,
    failure_reason varchar(120) NULL,
    failure_detail text NULL,
    auto_paused_reason varchar(40) NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_trade_cycle_log_cycle_stage
        CHECK (cycle_stage IN (
            'READY',
            'RECONCILE_PENDING',
            'INPUT_LOADING',
            'LLM_CALLING',
            'DECISION_VALIDATING',
            'ORDER_PLANNING',
            'ORDER_EXECUTING',
            'PORTFOLIO_UPDATING',
            'COMPLETED',
            'FAILED'
        )),
    CONSTRAINT chk_trade_cycle_log_auto_paused_reason
        CHECK (auto_paused_reason IN ('reconcile_failed') OR auto_paused_reason IS NULL)
);

CREATE INDEX idx_trade_cycle_log_instance_started_at
    ON trade_cycle_log (strategy_instance_id, cycle_started_at DESC);

CREATE INDEX idx_trade_cycle_log_business_date
    ON trade_cycle_log (business_date);

CREATE TABLE trade_decision_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_cycle_log_id uuid NOT NULL REFERENCES trade_cycle_log (id),
    strategy_instance_id uuid NOT NULL REFERENCES strategy_instance (id),
    cycle_started_at timestamptz NOT NULL,
    cycle_finished_at timestamptz NOT NULL,
    business_date date NOT NULL,
    cycle_status varchar(20) NOT NULL,
    summary text NULL,
    confidence numeric(5,4) NULL,
    failure_reason varchar(120) NULL,
    failure_detail text NULL,
    settings_snapshot_json jsonb NOT NULL,
    session_id uuid NULL,
    engine_name varchar(40) NULL,
    model_name varchar(120) NULL,
    request_text text NULL,
    response_text text NULL,
    stdout_text text NULL,
    stderr_text text NULL,
    input_tokens integer NULL,
    output_tokens integer NULL,
    estimated_cost numeric(19,8) NULL,
    timeout_seconds integer NULL,
    exit_code integer NULL,
    call_status varchar(30) NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trade_decision_log_trade_cycle_log_id
        UNIQUE (trade_cycle_log_id),
    CONSTRAINT chk_trade_decision_log_cycle_status
        CHECK (cycle_status IN ('EXECUTE', 'HOLD', 'FAILED')),
    CONSTRAINT chk_trade_decision_log_call_status
        CHECK (call_status IN (
            'SUCCESS',
            'TIMEOUT',
            'AUTH_ERROR',
            'NON_ZERO_EXIT',
            'EMPTY_OUTPUT',
            'INVALID_OUTPUT'
        ) OR call_status IS NULL)
);

CREATE INDEX idx_trade_decision_log_instance_started_at
    ON trade_decision_log (strategy_instance_id, cycle_started_at DESC);

CREATE INDEX idx_trade_decision_log_business_date
    ON trade_decision_log (business_date);

CREATE TABLE trade_order_intent (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_decision_log_id uuid NOT NULL REFERENCES trade_decision_log (id),
    sequence_no integer NOT NULL,
    symbol_code varchar(40) NOT NULL,
    side varchar(10) NOT NULL,
    quantity numeric(19,8) NOT NULL,
    order_type varchar(20) NOT NULL,
    price numeric(19,8) NULL,
    rationale text NOT NULL,
    evidence_json jsonb NOT NULL,
    execution_blocked_reason varchar(120) NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trade_order_intent_decision_sequence
        UNIQUE (trade_decision_log_id, sequence_no),
    CONSTRAINT chk_trade_order_intent_side
        CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_trade_order_intent_order_type
        CHECK (order_type IN ('MARKET', 'LIMIT'))
);

CREATE INDEX idx_trade_order_intent_decision_log_id
    ON trade_order_intent (trade_decision_log_id);

CREATE INDEX idx_trade_order_intent_symbol_code
    ON trade_order_intent (symbol_code);

CREATE TABLE trade_order (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_order_intent_id uuid NOT NULL REFERENCES trade_order_intent (id),
    strategy_instance_id uuid NOT NULL REFERENCES strategy_instance (id),
    client_order_id varchar(120) NOT NULL,
    broker_order_no varchar(120) NULL,
    execution_mode varchar(20) NOT NULL,
    order_status varchar(30) NOT NULL,
    requested_quantity numeric(19,8) NOT NULL,
    requested_price numeric(19,8) NULL,
    filled_quantity numeric(19,8) NOT NULL DEFAULT 0,
    avg_filled_price numeric(19,8) NULL,
    requested_at timestamptz NOT NULL,
    accepted_at timestamptz NULL,
    filled_at timestamptz NULL,
    failed_at timestamptz NULL,
    failure_reason varchar(120) NULL,
    broker_response_json jsonb NULL,
    portfolio_after_json jsonb NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trade_order_client_order_id
        UNIQUE (client_order_id),
    CONSTRAINT chk_trade_order_execution_mode
        CHECK (execution_mode IN ('paper', 'live'))
);

CREATE INDEX idx_trade_order_instance_requested_at
    ON trade_order (strategy_instance_id, requested_at DESC);

CREATE INDEX idx_trade_order_trade_order_intent_id
    ON trade_order (trade_order_intent_id);

CREATE INDEX idx_trade_order_status_requested_at
    ON trade_order (order_status, requested_at DESC);

CREATE TABLE audit_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at timestamptz NOT NULL,
    business_date date NOT NULL,
    actor_type varchar(20) NOT NULL,
    actor_id uuid NULL,
    target_type varchar(60) NOT NULL,
    target_id uuid NULL,
    action_type varchar(60) NOT NULL,
    before_json jsonb NULL,
    after_json jsonb NULL,
    summary_json jsonb NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_target
    ON audit_log (target_type, target_id, occurred_at DESC);

CREATE INDEX idx_audit_log_actor
    ON audit_log (actor_type, actor_id, occurred_at DESC);

CREATE TABLE ops_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at timestamptz NOT NULL,
    business_date date NOT NULL,
    event_type varchar(80) NOT NULL,
    strategy_instance_id uuid NULL REFERENCES strategy_instance (id),
    service_name varchar(60) NOT NULL,
    status_code varchar(40) NOT NULL,
    message text NULL,
    payload_json jsonb NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ops_event_instance_occurred_at
    ON ops_event (strategy_instance_id, occurred_at DESC);

CREATE INDEX idx_ops_event_service_status
    ON ops_event (service_name, status_code, occurred_at DESC);
