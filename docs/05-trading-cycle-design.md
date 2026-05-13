# 05. 트레이딩 사이클 설계

## 1. 문서 목적과 범위

이 문서는 [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md), [01-system-architecture.md](/Users/jongsoobae/workspace/alt-java/docs/01-system-architecture.md), [02-domain-model.md](/Users/jongsoobae/workspace/alt-java/docs/02-domain-model.md), [03-database-schema.md](/Users/jongsoobae/workspace/alt-java/docs/03-database-schema.md), [04-api-spec.md](/Users/jongsoobae/workspace/alt-java/docs/04-api-spec.md)를 기준으로 주기형 트레이딩 사이클의 실행 순서와 실패 처리 규칙을 고정한다.

목표:

- 한 인스턴스의 사이클 시작부터 종료까지 순서 고정
- `paper`/`live` 공통 흐름과 차이점 고정
- `FAILED`, `auto_paused`, reconcile, 비용 알림 처리 순서 고정
- 스케줄러, 워커, 주문 어댑터 구현 기준 제공

비범위:

- 브로커 API 상세 프로토콜
- LLM 프롬프트 본문 설계
- 외부 수집기 구현 상세

## 2. 핵심 원칙

- 모든 전략은 동일한 주기형 사이클 구조를 따른다.
- 한 인스턴스의 사이클은 반드시 직렬 실행한다.
- 사이클 시작 시점의 설정값을 고정해 사용한다.
- 사이클 도중 저장된 설정 변경은 다음 사이클부터 반영한다.
- 실제로 시작된 사이클은 `trade_cycle_log`로 저장한다.
- `trade_cycle_log.cycle_stage`는 `READY / RECONCILE_PENDING / INPUT_LOADING / LLM_CALLING / DECISION_VALIDATING / ORDER_PLANNING / ORDER_EXECUTING / PORTFOLIO_UPDATING / COMPLETED / FAILED`만 사용한다.
- 판단 로그는 `EXECUTE / HOLD / FAILED`만 저장한다.
- 시작 자체가 되지 않은 스킵은 제품 DB가 아니라 애플리케이션 로그와 운영 이벤트로만 남긴다.
- `paper`와 `live`는 같은 상위 사이클 구조를 공유하고 주문 실행 단계만 달라진다.

## 3. 사이클 대상 선택

### 3.1 스케줄 대상

트레이딩 워커는 다음 조건을 모두 만족하는 인스턴스만 스케줄 대상으로 본다.

- `lifecycle_state = active`
- `auto_paused_reason is null`
- 현재 KST 시각이 인스턴스 실행 시간대 안에 있음

다음 인스턴스는 신규 사이클을 시작하지 않는다.

- `draft`
- `inactive`
- `auto_paused_reason != null`

### 3.2 직렬 실행 보장

- 인스턴스별 직렬성은 `scheduled_tasks` 테이블의 `(task_name, task_instance)` 프라이머리 키와 `picked` 플래그로 보장한다. 외부 락(Redis 등)은 사용하지 않는다.
- `task_instance = strategyInstanceId` 매핑이라 같은 인스턴스의 row는 정확히 1개만 존재하며, 한 워커가 잡으면 `picked=true`로 마킹되어 다른 워커가 폴링 쿼리에서 못 잡는다.
- 사이클이 자기 `cycleMinutes`보다 오래 걸리면 db-scheduler의 `FixedDelay` 의미상 다음 실행이 자연스럽게 미뤄진다(catch-up 없음).
- 워커 비정상 종료 시 `picked=true`로 남은 row는 `last_heartbeat` stale 감지로 회수된다.

### 3.3 스케줄 등록과 해제

- 인스턴스 등록 주체는 **상태 기반 reconciler**다. 트레이딩 워커가 1분 주기로 `strategy_instance.lifecycle_state` 와 `scheduled_tasks`를 비교해 동기화한다.
  - `active` ∧ `auto_paused=null` 인스턴스 중 `scheduled_tasks`에 없는 것 → `scheduleIfNotExists`
  - `scheduled_tasks`에 있으나 위 조건을 만족하지 않는 인스턴스 → `cancel`
- web-app은 lifecycle 변경 시 DB 컬럼만 갱신하면 충분하다. trading-worker로의 직접 이벤트 전파는 없다(state-based, 분산 시스템 표준 패턴).

### 3.4 프롬프트 입력 스펙 위치

- 프롬프트 입력 스펙(사이클이 모아야 할 소스 종류와 종목 scope)은 별도 JSON 컬럼이 아니라 **prompt 본문 상단의 YAML frontmatter**(prompt 본문 맨 위 `---`로 둘러싸인 메타데이터 블록)로 선언된다.
- 운영자는 prompt 편집 한 곳에서 입력 스펙과 본문 템플릿을 함께 다룬다. 같은 인스턴스의 다른 prompt 버전은 서로 다른 입력 스펙을 가질 수 있다.
- 구 `strategy_template.default_input_spec_json` / `strategy_instance.input_spec_override_json` 컬럼은 호환성 보존용이며, 사이클은 더 이상 읽지 않는다.

## 4. 사이클 상태 요약

실제로 시작된 사이클은 `trade_cycle_log` row 하나로 저장하며, 구현상 다음 상태를 거친다.

1. `READY`
2. `RECONCILE_PENDING`
3. `INPUT_LOADING`
4. `LLM_CALLING`
5. `DECISION_VALIDATING`
6. `ORDER_PLANNING`
7. `ORDER_EXECUTING`
8. `PORTFOLIO_UPDATING`
9. `COMPLETED`
10. `FAILED`

이 상태는 `trade_cycle_log.cycle_stage`와 구조 로그/메트릭 태그로 함께 남긴다.

## 5. 표준 사이클 흐름

### 5.1 전체 순서

한 번의 정상 사이클은 아래 순서를 따른다.

1. db-scheduler가 `scheduled_tasks.execution_time` 도래한 row를 `FOR UPDATE SKIP LOCKED`로 pick (이게 곧 직렬성 보장)
2. `active` / `auto_paused` / 실행 시간대 재확인
3. `trade_cycle_log(READY)` 생성
4. `live`면 `RECONCILE_PENDING`
5. 판단 시점 설정값 고정
6. `INPUT_LOADING`
   - `PromptInputSpecParser`(prompt 상단 frontmatter를 분리해 `PromptInputSpec` 산출)가 활성 prompt 본문을 받아 `sources`/`scope`를 추출
   - `PromptContextAssembler`(frontmatter에 선언된 소스만 모아 종목별 컨텍스트로 합치는 어셈블러)가 시세/펀더멘털/뉴스/공시/매크로/호가 중 선언된 항목만 조회해 종목별 데이터 묶음 생성
   - `PromptTemplateEngine`(Pebble; Jinja2 호환 Java 템플릿 엔진)이 본문 템플릿에 system 변수와 `stocks` 컬렉션을 채워 LLM 입력 텍스트를 만든다
7. `LLM_CALLING`
8. `DECISION_VALIDATING`
9. 판단 로그 생성
10. `ORDER_PLANNING`
11. `paper` 또는 `live` 주문 실행(`ORDER_EXECUTING`)
12. 포트폴리오 현재 상태 갱신(`PORTFOLIO_UPDATING`)
13. 비용 알림 후처리
14. `COMPLETED` 또는 `FAILED`로 종료

### 5.2 판단 시점 설정값 고정

사이클 시작 시 아래 값을 확정하고 이후 변경하지 않는다.

- 프롬프트 버전 (frontmatter + 본문 템플릿 일체)
- 트레이딩 모델 설정
- 인스턴스 실행 설정
- 감시 종목 목록

이 값은 `trade_decision_log.settings_snapshot_json`에 저장한다. 별도 "전략 입력 스펙" 항목은 더 이상 분리 저장하지 않으며, prompt 본문 안의 frontmatter가 그 역할을 겸한다.

## 6. reconcile 단계

### 6.1 적용 대상

- `live` 인스턴스: 필수
- `paper` 인스턴스: 수행하지 않음

### 6.2 목적

reconcile은 브로커의 실제 주문 상태와 내부 주문 상태를 맞추는 절차다.

다음 상황을 정리한다.

- 응답 타임아웃 후 실제 주문 접수 여부 미확정
- `accepted` 후 아직 `filled` 되지 않은 주문
- 부분체결 누적 반영
- 내부 장애로 기록이 뒤처진 주문

### 6.3 결과 처리

- 성공: 다음 단계 진행
- 실패: 인스턴스를 `auto_paused(reason=reconcile_failed)`로 전환하고 사이클 로그를 `FAILED`로 종료

이 경우:

- 새로운 판단 로그는 만들지 않는다.
- 운영 알림을 발생시킨다.
- 다음 신규 `live` 사이클은 운영자 해제 전까지 시작하지 않는다.

## 7. 입력 데이터 준비

### 7.1 입력 구성 원칙

입력 데이터는 활성 prompt 상단 **frontmatter**에 선언된 `sources`만 모은다. 더 이상 별도 `inputSpec` JSON 컬럼을 읽지 않는다.

frontmatter `sources` 허용 타입(소스 타입; 사이클이 어떤 데이터를 모을지 표시):

- `minute_bar` — 1분봉 (`lookback_minutes`)
- `fundamental` — 펀더멘털 스냅샷 (기본 키 보유)
- `news` — 뉴스 (`lookback_hours`)
- `disclosure` — DART 공시 (`lookback_hours`)
- `macro` — 매크로 일자 스냅샷 (글로벌, 종목 무관)
- `orderbook` — 장중 호가 단기 캐시 (Redis)

`scope`는 종목 범위(어떤 종목까지 컨텍스트에 넣을지)다.

- `held_only` — 현재 보유 중인 종목만
- `full_watchlist` — 인스턴스 감시 종목 전부

### 7.2 장중 호가 처리

- 장중 호가 원본은 PostgreSQL에 저장하지 않는다.
- collector-worker가 Redis TTL 2일 데이터로만 유지한다.
- 사이클은 frontmatter에 `orderbook`이 선언된 경우에만 Redis에서 읽어 입력값으로 사용한다.

### 7.3 입력 실패 처리

다음 중 하나라도 실패하면 `INPUT_ASSEMBLY_FAILED`로 분류한다.

- frontmatter 파싱 실패 (YAML 문법 오류, 허용되지 않은 source 타입 등)
- `PromptContextAssembler`에서 필수 source 조회 실패
- `PromptTemplateEngine` 렌더 실패 (Pebble syntax 오류, 정의되지 않은 변수 참조 등)

`INPUT_ASSEMBLY_FAILED` 발생 시:

- 사이클 로그를 `FAILED`로 종료한다.
- 해당 턴의 판단 로그를 `FAILED`로 기록한다.
- 신규 주문은 만들지 않는다.
- 운영 알림을 발생시킨다.

시장 데이터 장애만으로는 인스턴스를 `auto_paused`로 전환하지 않는다.

## 8. LLM 호출과 판단 검증

### 8.1 LLM 호출

사이클은 확정된 프롬프트와 입력값으로 LLM을 1회 호출한다.

저장 대상:

- `session_id`
- `engine_name`
- `model_name`
- `request_text`
- `response_text`
- `stdout_text`
- `stderr_text`
- `input_tokens`
- `output_tokens`
- `estimated_cost`
- `call_status`

### 8.2 실패 처리

다음은 모두 판단 로그 `FAILED`로 기록한다.

- subprocess timeout
- 인증 오류
- non-zero exit
- 빈 응답
- 구조적 파싱 실패
- 필수 필드 누락
- 허용되지 않은 상태값
- 내용 충돌

이 경우 사이클 로그도 `FAILED`로 종료한다.

부분 수용은 허용하지 않는다.

## 9. 판단 로그 생성 규칙

### 9.1 상태 결정

- `EXECUTE`
  - 판단 성공
  - 실행 가능한 주문 제안이 1건 이상 존재
- `HOLD`
  - 판단 성공
  - 주문 제안이 없음
- `FAILED`
  - LLM 호출 또는 판단 처리 단계 실패

### 9.2 저장 시점

- 판단 파싱과 검증이 끝난 뒤 `trade_decision_log`를 생성한다.
- `trade_decision_log`는 생성 시 `trade_cycle_log_id`를 함께 가진다.
- 주문 실행 전에도 `trade_decision_log`는 먼저 존재할 수 있다.
- 이후 생성되는 `trade_order_intent`, `trade_order`는 이 판단 로그에 연결된다.

## 10. 주문 제안과 안전검증

### 10.1 주문 제안 생성

판단 결과의 `orders[]`를 `trade_order_intent`로 변환한다.

저장값:

- `sequence_no`
- `symbol_code`
- `side`
- `quantity`
- `order_type`
- `price`
- `rationale`
- `evidence_json`

### 10.2 최소 안전검증

주문 제출 직전에 최소 검증만 수행한다.

검증 예시:

- 수량 양수 여부
- `MARKET`/`LIMIT`와 `price` 조합 일관성
- 매수 가능 현금 범위
- 매도 가능 보유 수량 범위

검증 실패 시:

- 해당 `trade_order_intent.execution_blocked_reason` 기록
- 실제 주문 제출은 생략
- 판단 로그 자체는 `FAILED`로 되돌리지 않는다

## 11. 주문 실행 단계

### 11.1 paper

- `trade_order_intent` 1건은 일반적으로 `trade_order` 1건으로 이어진다.
- 주문은 즉시 전량 체결로 처리한다.
- 부분체결은 시뮬레이션하지 않는다.
- 정상 흐름은 `requested -> accepted -> filled`다.
- `MARKET` 체결가는 주문 제출 시점의 최신 현재가 스냅샷을 사용한다.
- `LIMIT` 체결가는 제안된 `price`를 사용한다.

### 11.2 live

- `trade_order_intent` 1건이 `trade_order` 여러 건으로 분기될 수 있다.
- 사유:
  - 주문 거부
  - 부분체결
  - 재주문
  - 브로커 상태 정정

주문 상태머신:

- `requested`
- `accepted`
- `partial`
- `filled`
- `canceled`
- `rejected`
- `failed`

### 11.3 주문 API 실패

`live`에서 주문 API 실패 시:

- 해당 주문은 `trade_order`에 `failed` 또는 적절한 실패 상태로 기록
- 같은 사이클 안에서 즉시 재시도하지 않음
- 미확정 상태는 다음 사이클 전 reconcile에서 처리

## 12. 포트폴리오 갱신

### 12.1 현재 상태

주문 처리 후 현재 상태는 다음 테이블에 반영한다.

- `portfolio`
- `portfolio_position`

### 12.2 주문 직후 스냅샷

각 `trade_order`에는 주문 처리 직후 상태를 `portfolio_after_json`으로 남긴다.

의도:

- 별도 포트폴리오 이력 테이블 없이도 주문 직후 상태 추적 가능
- 판단/주문/자산 변화를 한 흐름으로 리뷰 가능

## 13. 비용 알림과 auto_paused

### 13.1 비용 상한

- 인스턴스별 일일 비용 상한은 시스템 파라미터다.
- 사이클 도중 상한을 초과하게 되더라도 그 사이클은 끝까지 처리한다.
- 사이클 종료 후 일일 추정 비용 상한 초과 운영 알림을 준다.

### 13.2 auto_paused 규칙

- `auto_paused`는 lifecycle 주상태가 아니다.
- `active` 인스턴스에만 붙는 보조 상태다.
- 허용 사유:
  - `reconcile_failed`
- `reconcile_failed`는 운영자 수동 해제로만 복구한다.

## 14. 스킵과 지연 처리

### 14.1 스킵 조건

다음 경우 해당 주기는 시작하지 않고 스킵한다.

- `auto_paused_reason != null`
- 인스턴스가 더 이상 `active`가 아님
- 실행 가능 시간대가 아님

직전 사이클이 자기 주기보다 길어진 경우는 db-scheduler `FixedDelay` 의미상 다음 실행 시각이 자연스럽게 미뤄지므로 별도 스킵 규칙은 없다.

### 14.2 표시 규칙

- 스킵 자체는 별도 판단 로그를 만들지 않는다.
- 시스템 상태와 운영 로그에는 "사이클 지연" 또는 스킵 사유를 남긴다.
- 대시보드 상태 카드에서 최근 지연 징후를 확인할 수 있어야 한다.

## 15. 관측성과 알림

### 15.1 구조 로그

모든 사이클은 최소 다음 식별자를 로그에 포함한다.

- `instanceId`
- `cycleId`
- `decisionLogId`
- `tradeOrderIntentId`
- `tradeOrderId`

### 15.2 ops_event

`ops_event`는 로그성 보조 저장소로 사용한다.

예시:

- 외부 API 장애
- LLM 호출 실패
- reconcile 실패
- `auto_paused` 전환
- 수집 지연

### 15.3 알림 발생 조건

예시:

- 판단 로그 `FAILED`
- 주문 API 실패
- reconcile 실패
- 일일 추정 비용 상한 초과
- `auto_paused(reason=reconcile_failed)`
- 활성 인스턴스의 다음 사이클 진입 자체가 불가능한 실행 오류

운영 알림 채널은 Telegram이다.

## 16. Prompt frontmatter + Pebble template

사이클이 LLM 입력을 만드는 방식은 prompt 본문 자체에 입력 스펙과 본문 템플릿이 함께 들어 있는 구조다.

### 16.1 frontmatter 형식

prompt 본문 맨 위에 `---`로 둘러싼 YAML 블록을 둔다.

```
---
sources:
  - {type: minute_bar, lookback_minutes: 120}
  - {type: fundamental}
  - {type: news, lookback_hours: 12}
scope: full_watchlist
---
```

- `sources` 허용 타입: `minute_bar`, `fundamental`, `news`, `disclosure`, `macro`, `orderbook` (총 6종)
- `scope`: `held_only` 또는 `full_watchlist`
- `sources`에 선언되지 않은 타입은 어셈블러가 조회하지 않는다.

### 16.2 Pebble 컨텍스트 변수

본문 템플릿은 다음 변수를 사용할 수 있다.

- system 변수 (사이클 단위 글로벌)
  - `current_time` — 사이클 시작 시각 (KST)
  - `cash_amount` — 현재 현금 잔고
  - `held_positions` — 현재 보유 종목 요약
- `stocks` — 종목 컬렉션. `{% for s in stocks %}` 루프로 순회. 각 항목은 다음 키를 가진 Map.
  - `code` — 종목코드
  - `name` — 종목명
  - `minute_bars` — frontmatter에 `minute_bar` 선언 시 채워짐
  - `fundamental` — `fundamental` 선언 시 채워짐
  - `news` — `news` 선언 시 채워짐
  - `disclosures` — `disclosure` 선언 시 채워짐
  - `orderbook` — `orderbook` 선언 시 채워짐
  - 헤더에 선언되지 않은 항목 키는 빈 문자열로 채워진다 (Pebble undefined 회피용).
- `macro` — frontmatter에 `macro` 선언 시 채워지는 글로벌 변수 (종목 무관 일자 스냅샷)

운영자가 사용 가능한 변수 카탈로그는 `GET /api/admin/prompt-vocabulary`에서 조회한다.

### 16.3 활성화 게이트

인스턴스 활성화 검증은 prompt frontmatter 파싱 가능 여부로 판단한다.

- `PromptInputSpecParser`가 활성 prompt 본문을 파싱해서 `PromptInputSpec`(sources + scope)을 만들 수 있어야 한다.
- 파싱 실패 시 `INSTANCE_NOT_ACTIVATABLE` 응답으로 활성화가 거절된다.
- 구 "입력 스펙 JSON 존재" 검증은 제거되었다. `strategy_template.default_input_spec_json` / `strategy_instance.input_spec_override_json` 컬럼은 호환성 보존용이며 v2에서 DROP 예정이다.

### 16.4 사이클 실패 매핑

frontmatter/Pebble 단계의 실패는 모두 `INPUT_ASSEMBLY_FAILED` 사유로 사이클을 `FAILED` 종료한다.

- YAML frontmatter 파싱 실패
- 허용되지 않은 source 타입/누락 필수 필드
- `PromptContextAssembler` 의 필수 source 조회 실패
- `PromptTemplateEngine` 렌더 실패 (Pebble syntax 오류, 정의되지 않은 변수, 잘못된 loop 등)

판단 로그도 `FAILED`로 기록한다. 부분 수용은 없다.

## 17. 구현 체크리스트

구현 완료 기준:

- `scheduled_tasks` 프라이머리 키 기반 인스턴스별 직렬 실행 (db-scheduler)
- `trade_cycle_log` 생성과 단계 갱신
- `paper` 전체 사이클 완성
- `live` reconcile 선행 정책 반영
- 판단 시점 설정값 고정 저장
- prompt frontmatter + Pebble template 기반 입력 조립 (`PromptInputSpecParser` → `PromptContextAssembler` → `PromptTemplateEngine`)
- `trade_decision_log` / `trade_order_intent` / `trade_order` 연결
- `portfolio_after_json` 저장
- 비용 상한 초과 알림과 `reconcile_failed` 기반 `auto_paused` 반영
- 구조 로그 / `ops_event` / Telegram 알림 연결
