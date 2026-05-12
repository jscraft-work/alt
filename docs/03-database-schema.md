# 03. Database Schema

## 1. 문서 목적과 범위

이 문서는 [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md), [01-system-architecture.md](/Users/jongsoobae/workspace/alt-java/docs/01-system-architecture.md), [02-domain-model.md](/Users/jongsoobae/workspace/alt-java/docs/02-domain-model.md)를 PostgreSQL 기준 물리 스키마로 옮기기 위한 기준선이다.

목표:

- 핵심 테이블과 컬럼 구조 고정
- PK/FK/UNIQUE/INDEX 기준 고정
- soft delete, optimistic lock, 파티셔닝, 보존 정책 반영 방식 고정
- JPA 매핑과 마이그레이션 설계의 기준 제공

비범위:

- 실제 Flyway/Liquibase 마이그레이션 파일
- JPA 어노테이션 상세
- 조회 전용 materialized view 상세
- 브로커/LLM 외부 DTO 구조

## 2. 설계 원칙

- DBMS는 PostgreSQL을 사용한다.
- 캐시는 Redis를 사용하며, 이 문서는 Redis 키 구조를 다루지 않는다.
- 가변 운영 테이블은 `version` 컬럼으로 optimistic lock을 적용한다.
- 삭제 이력이 필요한 운영 테이블은 hard delete 대신 `deleted_at` 기반 soft delete를 사용한다.
- append-only 이력 테이블은 update/delete를 허용하지 않는다.
- 시계열/대용량 테이블은 PostgreSQL 파티셔닝을 적용한다.
- 모든 시각 컬럼은 `timestamptz`로 저장하고, 애플리케이션은 KST 기준으로 기록한다.
- KST 일 경계가 중요한 테이블은 `business_date` 컬럼을 함께 둔다.
- enum은 PostgreSQL enum 대신 `varchar` + check constraint를 우선 사용한다.

## 3. 공통 규칙

### 3.1 네이밍

- 테이블/컬럼명은 `snake_case`
- PK 컬럼명은 기본적으로 `id`
- FK 컬럼명은 `<referenced_table>_id`
- 시간 컬럼은 `*_at`
- JSON 컬럼은 `*_json`
- 도메인 prefix는 `trade`, `market`, `news`, `disclosure`, `macro`, `strategy`처럼 역할 기준으로 고정한다.
- suffix 의미는 다음처럼 고정한다.
  - `_log`: append-only 이력 레코드
  - `_intent`: 실제 주문 전 단계의 제안 레코드
  - `_item`: 수집/목록/원천 데이터의 개별 항목 레코드
  - `_relation`: N:M 연결 레코드

### 3.2 기본 컬럼 규약

가변 운영 테이블 공통:

- `id uuid primary key default gen_random_uuid()`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`
- `version bigint not null default 0`
- `deleted_at timestamptz null`

append-only 이력 테이블 공통:

- `id uuid primary key default gen_random_uuid()`
- `created_at timestamptz not null`

### 3.3 soft delete 대상

soft delete 적용:

- `strategy_template`
- `strategy_instance`
- `broker_account`
- `asset_master`
- `strategy_instance_watchlist_relation`
- `app_user`

soft delete 미적용:

- `trade_cycle_log`
- `trade_decision_log`
- `trade_order_intent`
- `trade_order`
- `audit_log`
- `ops_event`
- 시계열 수집 테이블 전부

## 4. 핵심 테이블

### 4.1 전략 템플릿

#### `strategy_template`

컬럼:

- `id uuid pk`
- `name varchar(120) not null`
- `description text not null`
- `default_cycle_minutes integer not null`
- `default_prompt_text text not null`
- `default_input_spec_json jsonb not null`
- `default_execution_config_json jsonb not null`
- `default_trading_model_profile_id uuid not null`
- `created_at`
- `updated_at`
- `version`
- `deleted_at`

제약:

- `check (default_cycle_minutes between 1 and 30)`

비고:

- 템플릿 기본 입력 스펙과 실행 설정은 JSONB로 저장한다.
- 템플릿 프롬프트는 현재 기본값만 저장하고, 버전 이력은 인스턴스 프롬프트에서만 관리한다.

### 4.2 전략 인스턴스

#### `strategy_instance`

컬럼:

- `id uuid pk`
- `strategy_template_id uuid not null`
- `name varchar(120) not null`
- `lifecycle_state varchar(20) not null`
- `execution_mode varchar(20) not null`
- `broker_account_id uuid null`
- `budget_amount numeric(19,4) not null`
- `current_prompt_version_id uuid null`
- `trading_model_profile_id uuid null`
- `input_spec_override_json jsonb null`
- `execution_config_override_json jsonb null`
- `auto_paused_reason varchar(40) null`
- `auto_paused_at timestamptz null`
- `created_at`
- `updated_at`
- `version`
- `deleted_at`

제약:

- `check (lifecycle_state in ('draft','active','inactive'))`
- `check (execution_mode in ('paper','live'))`
- `check (budget_amount > 0)`
- `check (auto_paused_reason in ('reconcile_failed') or auto_paused_reason is null)`

비고:

- `web` 설정 변경은 이 테이블과 하위 설정 테이블에 즉시 반영된다.
- 실행 시 사용한 값은 `trade_decision_log.settings_snapshot_json`에 별도로 고정 저장한다.
- 하나의 연결 계좌는 같은 시점에 하나의 `active live` 인스턴스에만 연결될 수 있어야 하며, 이 규칙은 애플리케이션 검증과 optimistic lock으로 보장한다.
- 실제 인스턴스 설정은 `strategy_instance`, `strategy_instance_prompt_version`, `strategy_instance_watchlist_relation`와 참조 모델/템플릿 값의 조합으로 구성한다.

#### `strategy_instance_prompt_version`

컬럼:

- `id uuid pk`
- `strategy_instance_id uuid not null`
- `version_no integer not null`
- `prompt_text text not null`
- `change_note text null`
- `created_at timestamptz not null`
- `created_by uuid null`

제약:

- `unique (strategy_instance_id, version_no)`

인덱스:

- `idx_strategy_instance_prompt_version_instance_created_at (strategy_instance_id, created_at desc)`

#### `strategy_instance_watchlist_relation`

컬럼:

- `id uuid pk`
- `strategy_instance_id uuid not null`
- `asset_master_id uuid not null`
- `created_at`
- `updated_at`
- `version`
- `deleted_at`

부분 unique 인덱스:

- `uq_strategy_instance_watchlist_relation_live (strategy_instance_id, asset_master_id) where deleted_at is null`

### 4.3 모델과 파라미터

#### `llm_model_profile`

컬럼:

- `id uuid pk`
- `purpose varchar(40) not null`
- `provider varchar(40) not null`
- `model_name varchar(120) not null`
- `command varchar(200) not null`
- `args_json jsonb not null`
- `default_timeout_seconds integer not null`
- `enabled boolean not null default true`
- `created_at`
- `updated_at`
- `version`
- `deleted_at`

제약:

- `check (purpose in ('trading_decision','daily_report','news_assessment'))`

인덱스:

- `idx_llm_model_profile_purpose_enabled (purpose, enabled)`

비고:

- 뉴스 유용성 판정 모델도 이 테이블에서 글로벌 설정으로 관리한다.

#### `system_parameter`

컬럼:

- `parameter_key varchar(120) primary key`
- `value_json jsonb not null`
- `description text null`
- `updated_at timestamptz not null`
- `version bigint not null default 0`

### 4.4 계좌와 종목 마스터

#### `broker_account`

컬럼:

- `id uuid pk`
- `broker_code varchar(40) not null`
- `broker_account_no varchar(120) not null`
- `account_alias varchar(120) null`
- `account_masked varchar(120) not null`
- `created_at`
- `updated_at`
- `version`
- `deleted_at`

제약:

- `unique (broker_code, broker_account_no)`

#### `asset_master`

컬럼:

- `id uuid pk`
- `symbol_code varchar(40) not null`
- `symbol_name varchar(200) not null`
- `market_type varchar(40) not null`
- `dart_corp_code varchar(20) null`
- `is_hidden boolean not null default false`
- `created_at`
- `updated_at`
- `version`
- `deleted_at`

제약:

- `unique (symbol_code)`

### 4.5 사이클 실행 로그

#### `trade_cycle_log`

컬럼:

- `id uuid pk`
- `strategy_instance_id uuid not null`
- `cycle_started_at timestamptz not null`
- `cycle_finished_at timestamptz null`
- `business_date date not null`
- `cycle_stage varchar(40) not null`
- `failure_reason varchar(120) null`
- `failure_detail text null`
- `auto_paused_reason varchar(40) null`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

제약:

- `check (cycle_stage in ('READY','RECONCILE_PENDING','INPUT_LOADING','LLM_CALLING','DECISION_VALIDATING','ORDER_PLANNING','ORDER_EXECUTING','PORTFOLIO_UPDATING','COMPLETED','FAILED'))`
- `check (auto_paused_reason in ('reconcile_failed') or auto_paused_reason is null)`

인덱스:

- `idx_trade_cycle_log_instance_started_at (strategy_instance_id, cycle_started_at desc)`
- `idx_trade_cycle_log_business_date (business_date)`

파티셔닝:

- 월 단위 range partition by `business_date`

비고:

- 실제로 시작된 사이클만 저장한다.
- 락 획득 실패, 실행 시간대 밖, 이미 진행 중이라 시작되지 못한 스킵은 이 테이블에 저장하지 않는다.

### 4.6 판단 로그와 LLM 원문

#### `trade_decision_log`

컬럼:

- `id uuid pk`
- `trade_cycle_log_id uuid not null`
- `strategy_instance_id uuid not null`
- `cycle_started_at timestamptz not null`
- `cycle_finished_at timestamptz not null`
- `business_date date not null`
- `cycle_status varchar(20) not null`
- `summary text null`
- `confidence numeric(5,4) null`
- `failure_reason varchar(120) null`
- `failure_detail text null`
- `settings_snapshot_json jsonb not null`
- `session_id uuid null`
- `engine_name varchar(40) null`
- `model_name varchar(120) null`
- `request_text text null`
- `response_text text null`
- `stdout_text text null`
- `stderr_text text null`
- `input_tokens integer null`
- `output_tokens integer null`
- `estimated_cost numeric(19,8) null`
- `timeout_seconds integer null`
- `exit_code integer null`
- `call_status varchar(30) null`
- `created_at timestamptz not null`

제약:

- `unique (trade_cycle_log_id)`
- `check (cycle_status in ('EXECUTE','HOLD','FAILED'))`
- `check (call_status in ('SUCCESS','TIMEOUT','AUTH_ERROR','NON_ZERO_EXIT','EMPTY_OUTPUT','INVALID_OUTPUT') or call_status is null)`

인덱스:

- `idx_trade_decision_log_instance_started_at (strategy_instance_id, cycle_started_at desc)`
- `idx_trade_decision_log_business_date (business_date)`

파티셔닝:

- 월 단위 range partition by `business_date`

보존:

- 최근 90일 hot, 이후 cold 보존 대상인 LLM 요청/응답 원문도 `trade_decision_log` 안에서 같은 기준으로 관리한다.

### 4.7 주문 제안과 실제 주문

#### `trade_order_intent`

컬럼:

- `id uuid pk`
- `trade_decision_log_id uuid not null`
- `sequence_no integer not null`
- `symbol_code varchar(40) not null`
- `side varchar(10) not null`
- `quantity numeric(19,8) not null`
- `order_type varchar(20) not null`
- `price numeric(19,8) null`
- `rationale text not null`
- `evidence_json jsonb not null`
- `execution_blocked_reason varchar(120) null`
- `created_at timestamptz not null`

제약:

- `unique (trade_decision_log_id, sequence_no)`
- `check (side in ('BUY','SELL'))`
- `check (order_type in ('MARKET','LIMIT'))`

인덱스:

- `idx_trade_order_intent_decision_log_id (trade_decision_log_id)`
- `idx_trade_order_intent_symbol_code (symbol_code)`

비고:

- `paper`에서는 일반적으로 `trade_order_intent` 1건이 `trade_order` 1건으로 바로 이어진다.
- `live`에서는 주문 거부, 부분체결, 재주문 분기 가능성이 있으므로 `trade_order_intent -> N trade_order`를 허용한다.

#### `trade_order`

컬럼:

- `id uuid pk`
- `trade_order_intent_id uuid not null`
- `strategy_instance_id uuid not null`
- `client_order_id varchar(120) not null`
- `broker_order_no varchar(120) null`
- `execution_mode varchar(20) not null`
- `order_status varchar(30) not null`
- `requested_quantity numeric(19,8) not null`
- `requested_price numeric(19,8) null`
- `filled_quantity numeric(19,8) not null default 0`
- `avg_filled_price numeric(19,8) null`
- `requested_at timestamptz not null`
- `accepted_at timestamptz null`
- `filled_at timestamptz null`
- `failed_at timestamptz null`
- `failure_reason varchar(120) null`
- `broker_response_json jsonb null`
- `portfolio_after_json jsonb null`
- `created_at timestamptz not null`

제약:

- `unique (client_order_id)`
- `check (execution_mode in ('paper','live'))`

인덱스:

- `idx_trade_order_instance_requested_at (strategy_instance_id, requested_at desc)`
- `idx_trade_order_trade_order_intent_id (trade_order_intent_id)`
- `idx_trade_order_status_requested_at (order_status, requested_at desc)`

파티셔닝:

- 월 단위 range partition by `requested_at`

비고:

- `client_order_id`는 실제 주문 제출 단위마다 새로 발급한다.

### 4.8 포트폴리오 현재 상태

#### `portfolio`

컬럼:

- `strategy_instance_id uuid primary key`
- `cash_amount numeric(19,4) not null`
- `total_asset_amount numeric(19,4) not null`
- `realized_pnl_today numeric(19,4) not null`
- `updated_at timestamptz not null`
- `version bigint not null default 0`

#### `portfolio_position`

컬럼:

- `id uuid pk`
- `strategy_instance_id uuid not null`
- `symbol_code varchar(40) not null`
- `quantity numeric(19,8) not null`
- `avg_buy_price numeric(19,8) not null`
- `last_mark_price numeric(19,8) null`
- `unrealized_pnl numeric(19,4) null`
- `updated_at timestamptz not null`
- `version bigint not null default 0`

제약:

- `unique (strategy_instance_id, symbol_code)`

### 4.9 시장/뉴스/공시/매크로

#### `market_price_item`

컬럼:

- `id uuid pk`
- `symbol_code varchar(40) not null`
- `snapshot_at timestamptz not null`
- `business_date date not null`
- `last_price numeric(19,8) not null`
- `open_price numeric(19,8) null`
- `high_price numeric(19,8) null`
- `low_price numeric(19,8) null`
- `volume numeric(19,4) null`
- `source_name varchar(40) not null`
- `created_at timestamptz not null`

인덱스:

- `idx_market_price_item_symbol_snapshot_at (symbol_code, snapshot_at desc)`
- `idx_market_price_item_business_date (business_date)`

파티셔닝:

- 월 단위 range partition by `business_date`

#### `market_minute_item`

컬럼:

- `id uuid pk`
- `symbol_code varchar(40) not null`
- `bar_time timestamptz not null`
- `business_date date not null`
- `open_price numeric(19,8) not null`
- `high_price numeric(19,8) not null`
- `low_price numeric(19,8) not null`
- `close_price numeric(19,8) not null`
- `volume numeric(19,4) null`
- `source_name varchar(40) not null`
- `created_at timestamptz not null`

제약:

- `unique (symbol_code, bar_time, source_name)`

인덱스:

- `idx_market_minute_item_symbol_bar_time (symbol_code, bar_time desc)`

파티셔닝:

- 월 단위 range partition by `business_date`

#### `market_intraday_quote_item`

비고:

- 이 데이터는 PostgreSQL 테이블로 영구 저장하지 않는다.
- collector-worker가 Redis에 TTL 2일로 저장하는 단기 입력 데이터로만 사용한다.
- 키 구조와 TTL 정책은 Redis 설계에서 별도로 다룬다.

#### `news_item`

컬럼:

- `id uuid pk`
- `provider_name varchar(40) not null`
- `external_news_id varchar(200) null`
- `title text not null`
- `article_url text not null`
- `published_at timestamptz not null`
- `summary text null`
- `usefulness_status varchar(30) not null`
- `usefulness_assessed_at timestamptz null`
- `usefulness_model_profile_id uuid null`
- `created_at timestamptz not null`

제약:

- `check (usefulness_status in ('useful','not_useful','unclassified'))`

인덱스:

- `idx_news_item_published_at (published_at desc)`
- `idx_news_item_usefulness_status (usefulness_status, published_at desc)`

파티셔닝:

- 월 단위 range partition by `published_at`

#### `news_asset_relation`

컬럼:

- `id uuid pk`
- `news_item_id uuid not null`
- `asset_master_id uuid not null`
- `created_at timestamptz not null`

제약:

- `unique (news_item_id, asset_master_id)`

#### `disclosure_item`

컬럼:

- `id uuid pk`
- `dart_corp_code varchar(20) not null`
- `disclosure_no varchar(120) not null`
- `title text not null`
- `published_at timestamptz not null`
- `preview_text text null`
- `document_url text not null`
- `created_at timestamptz not null`

제약:

- `unique (disclosure_no)`

인덱스:

- `idx_disclosure_item_corp_published_at (dart_corp_code, published_at desc)`

파티셔닝:

- 월 단위 range partition by `published_at`

#### `macro_item`

컬럼:

- `id uuid pk`
- `base_date date not null`
- `payload_json jsonb not null`
- `created_at timestamptz not null`

제약:

- `unique (base_date)`

### 4.10 운영자/감사/운영 이벤트

#### `app_user`

컬럼:

- `id uuid pk`
- `login_id varchar(120) not null`
- `password_hash varchar(255) not null`
- `display_name varchar(120) not null`
- `role_code varchar(40) not null`
- `enabled boolean not null default true`
- `last_login_at timestamptz null`
- `created_at`
- `updated_at`
- `version`
- `deleted_at`

제약:

- `unique (login_id)`

#### `audit_log`

컬럼:

- `id uuid pk`
- `occurred_at timestamptz not null`
- `business_date date not null`
- `actor_type varchar(20) not null`
- `actor_id uuid null`
- `target_type varchar(60) not null`
- `target_id uuid null`
- `action_type varchar(60) not null`
- `before_json jsonb null`
- `after_json jsonb null`
- `summary_json jsonb null`
- `created_at timestamptz not null`

인덱스:

- `idx_audit_log_target (target_type, target_id, occurred_at desc)`
- `idx_audit_log_actor (actor_type, actor_id, occurred_at desc)`

파티셔닝:

- 월 단위 range partition by `business_date`

#### `ops_event`

컬럼:

- `id uuid pk`
- `occurred_at timestamptz not null`
- `business_date date not null`
- `event_type varchar(80) not null`
- `strategy_instance_id uuid null`
- `service_name varchar(60) not null`
- `status_code varchar(40) not null`
- `message text null`
- `payload_json jsonb null`
- `created_at timestamptz not null`

인덱스:

- `idx_ops_event_instance_occurred_at (strategy_instance_id, occurred_at desc)`
- `idx_ops_event_service_status (service_name, status_code, occurred_at desc)`

파티셔닝:

- 월 단위 range partition by `business_date`

비고:

- `ops_event`는 장애 분석과 운영 추적을 돕는 로그성 테이블이다.
- 시스템의 1차 원본 로그를 대체하지 않으며, 구조 로그/모니터링과 함께 보조적으로 사용한다.

## 5. FK 관계

핵심 FK:

- `strategy_instance.strategy_template_id -> strategy_template.id`
- `strategy_instance.broker_account_id -> broker_account.id`
- `strategy_template.default_trading_model_profile_id -> llm_model_profile.id`
- `strategy_instance.trading_model_profile_id -> llm_model_profile.id`
- `strategy_instance.current_prompt_version_id -> strategy_instance_prompt_version.id`
- `strategy_instance_prompt_version.strategy_instance_id -> strategy_instance.id`
- `strategy_instance_watchlist_relation.strategy_instance_id -> strategy_instance.id`
- `strategy_instance_watchlist_relation.asset_master_id -> asset_master.id`
- `trade_cycle_log.strategy_instance_id -> strategy_instance.id`
- `trade_decision_log.trade_cycle_log_id -> trade_cycle_log.id`
- `trade_decision_log.strategy_instance_id -> strategy_instance.id`
- `trade_order_intent.trade_decision_log_id -> trade_decision_log.id`
- `trade_order.trade_order_intent_id -> trade_order_intent.id`
- `trade_order.strategy_instance_id -> strategy_instance.id`
- `portfolio.strategy_instance_id -> strategy_instance.id`
- `portfolio_position.strategy_instance_id -> strategy_instance.id`
- `news_asset_relation.news_item_id -> news_item.id`
- `news_asset_relation.asset_master_id -> asset_master.id`
- `news_item.usefulness_model_profile_id -> llm_model_profile.id`
- `ops_event.strategy_instance_id -> strategy_instance.id`

비고:

- `strategy_instance.current_prompt_version_id`는 같은 인스턴스 소유의 프롬프트 버전만 가리키도록 애플리케이션 검증을 추가한다.
- `trade_order`는 `strategy_instance_id`를 중복 저장해 인스턴스 기준 조회 인덱스를 단순화한다.

## 6. soft delete와 optimistic lock 반영 방식

soft delete:

- JPA/Hibernate에서는 `@SQLDelete` + `deleted_at` 갱신 방식 적용
- 조회 기본 조건은 `deleted_at is null`
- append-only 테이블에는 soft delete를 적용하지 않는다

optimistic lock:

- `version bigint`를 사용한다
- 설정 저장 충돌이 중요한 테이블에만 적용한다
- 최소 적용 대상:
  - `strategy_template`
  - `strategy_instance`
  - `broker_account`
  - `asset_master`
  - `llm_model_profile`
  - `system_parameter`
  - `strategy_instance_watchlist_relation`
  - `app_user`

## 7. 파티셔닝과 보존 정책 반영

파티셔닝 기본 단위:

- 고빈도 시계열: 월 단위
- 2일 보존 호가 원본: 일 단위

파티셔닝 대상:

- `trade_decision_log`
- `trade_cycle_log`
- `trade_order`
- `market_price_item`
- `market_minute_item`
- `news_item`
- `disclosure_item`
- `audit_log`
- `ops_event`

보존 정책 반영:

- `market_minute_item`: 90일 hot
- `market_price_item`: 30일 hot
- `news_item`, `disclosure_item`: 1년 hot
- `trade_cycle_log`: 90일 hot, 이후 cold archive 영구 보존
- `trade_decision_log`, `trade_order`: 영구
- `audit_log`, `ops_event`: 영구

## 8. 조회 성능 기준 인덱스

대시보드:

- `trade_decision_log(strategy_instance_id, cycle_started_at desc)`
- `trade_cycle_log(strategy_instance_id, cycle_started_at desc)`
- `trade_order(strategy_instance_id, requested_at desc)`
- `market_price_item(symbol_code, snapshot_at desc)`
- `market_minute_item(symbol_code, bar_time desc)`

설정 화면:

- `strategy_instance_watchlist_relation(strategy_instance_id, asset_master_id)`
- `strategy_instance_prompt_version(strategy_instance_id, created_at desc)`

운영 화면:

- `audit_log(target_type, target_id, occurred_at desc)`
- `ops_event(service_name, status_code, occurred_at desc)`
- `ops_event(strategy_instance_id, occurred_at desc)`

## 9. 구현 시 주의사항

- `trade_decision_log.settings_snapshot_json`은 조회 편의를 위해 JSONB로 저장하되, 포맷은 애플리케이션 레벨 스키마로 엄격히 관리한다.
- `trade_decision_log.request_text`와 `response_text`는 저작권/보안 이슈가 있으므로 무분별한 복제 저장을 피한다.
- `trade_cycle_log`는 상태 진행에 따라 같은 row를 갱신하며, 최종적으로 `COMPLETED` 또는 `FAILED` 상태로 끝난다.
- `trade_order.portfolio_after_json`은 해당 주문 처리 직후 포트폴리오 상태를 기록한다.
- 인스턴스 설정 변경 이력은 별도 테이블로 두지 않고 `audit_log`로만 남긴다.
- `market_intraday_quote_item`은 DB 테이블이 아니라 Redis TTL 데이터로 운영한다.
- `collector-worker` 단일 인스턴스 운영을 전제로 하지만, 파티션 키와 unique 제약은 중복 수집에도 견딜 수 있게 설계한다.
