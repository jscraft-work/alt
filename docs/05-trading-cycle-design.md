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

- 인스턴스별 직렬성은 Redis 락으로 보장한다.
- 락은 사이클 시작 시 1회 획득 시도한다.
- 락 TTL은 2분이다.
- 락 획득 실패 시 해당 주기는 스킵한다.
- 워커 비정상 종료 시 락은 TTL 만료로 정리한다.

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

1. 스케줄 시각 도래
2. Redis 락 획득 시도
3. `active` / `auto_paused` / 실행 시간대 재확인
4. `trade_cycle_log(READY)` 생성
5. `live`면 `RECONCILE_PENDING`
6. 판단 시점 설정값 고정
7. `INPUT_LOADING`
8. `LLM_CALLING`
9. `DECISION_VALIDATING`
10. 판단 로그 생성
11. `ORDER_PLANNING`
12. `paper` 또는 `live` 주문 실행(`ORDER_EXECUTING`)
13. 포트폴리오 현재 상태 갱신(`PORTFOLIO_UPDATING`)
14. 비용 알림 후처리
15. `COMPLETED` 또는 `FAILED`로 종료

### 5.2 판단 시점 설정값 고정

사이클 시작 시 아래 값을 확정하고 이후 변경하지 않는다.

- 프롬프트 버전
- 트레이딩 모델 설정
- 전략 입력 스펙
- 인스턴스 실행 설정
- 감시 종목 목록

이 값은 `trade_decision_log.settings_snapshot_json`에 저장한다.

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

입력 데이터는 확정된 입력 스펙에 따라 수집한다.

예시:

- 현재가
- 시장 스냅샷
- 1분봉
- 뉴스
- 공시
- 매크로
- 장중 호가 단기 캐시

### 7.2 장중 호가 처리

- 장중 호가 원본은 PostgreSQL에 저장하지 않는다.
- collector-worker가 Redis TTL 2일 데이터로만 유지한다.
- 사이클은 필요 시 Redis에서 읽어 입력값으로 사용한다.

### 7.3 입력 실패 처리

필수 입력 데이터 조회 또는 입력 검증에 실패하면:

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

- Redis 락 획득 실패
- 이전 사이클이 아직 진행 중
- `auto_paused_reason != null`
- 인스턴스가 더 이상 `active`가 아님
- 실행 가능 시간대가 아님

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

## 16. 구현 체크리스트

구현 완료 기준:

- 인스턴스별 Redis 락 직렬 실행
- `trade_cycle_log` 생성과 단계 갱신
- `paper` 전체 사이클 완성
- `live` reconcile 선행 정책 반영
- 판단 시점 설정값 고정 저장
- `trade_decision_log` / `trade_order_intent` / `trade_order` 연결
- `portfolio_after_json` 저장
- 비용 상한 초과 알림과 `reconcile_failed` 기반 `auto_paused` 반영
- 구조 로그 / `ops_event` / Telegram 알림 연결
