# 09. 관측성 및 운영 런북

## 1. 문서 목적과 범위

이 문서는 [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md) §8.2.1, §9.3, §10.9, §13과 [01-system-architecture.md](/Users/jongsoobae/workspace/alt-java/docs/01-system-architecture.md), [05-trading-cycle-design.md](/Users/jongsoobae/workspace/alt-java/docs/05-trading-cycle-design.md), [07-external-integrations.md](/Users/jongsoobae/workspace/alt-java/docs/07-external-integrations.md)를 기준으로 운영 관측성과 장애 대응 절차를 고정한다.

목표:

- 구조 로그/감사 로그/`ops_event` 책임 경계 고정
- 시스템 상태 카드 판정 규칙 고정
- Telegram 알림 발송 정책 고정
- health/readiness 점검 항목 고정
- 운영자가 따라할 수 있는 수동 대응 절차 고정

비범위:

- APM/모니터링 SaaS 선택
- 메트릭 시각화 도구 설정 상세
- SLA 정의

## 2. 관측 채널 구조

세 채널을 명확히 구분해서 사용한다.

| 채널 | 대상 | 보존 | 주 사용처 |
|------|------|------|-----------|
| 구조 로그(JSON stdout) | 모든 요청/사이클/외부 호출 | 단기 (운영 기준 7~30일) | 디버깅, APM |
| `audit_log` (DB) | 운영자 행동, 인증 사건 | 영구 (cold) | 감사 추적 |
| `ops_event` (DB) | 자동 운영 사건, 외부 장애, 사이클 실패 | 영구 (cold) | 운영 화면, 알림 입력 |

원칙:

- 사용자/운영자 행동은 `audit_log`, 시스템이 자율로 만든 사건은 `ops_event`.
- 단기 디버깅은 구조 로그, 장기 추적은 DB 두 테이블.
- 같은 사건을 두 테이블에 중복 저장하지 않는다.

## 3. 구조 로그 포맷

기본 출력: JSON, stdout. reverse proxy/컨테이너 stdout을 그대로 수집한다.

공통 필드:

- `timestamp` (ISO-8601, KST)
- `level`
- `logger`
- `message`
- `service` (`web-app` / `trading-worker` / `collector-worker`)
- `traceId`
- `spanId`

사이클/주문 관련 필드:

- `cycleId`
- `instanceId`
- `decisionId`
- `orderId`
- `phase` (`READY` / `INPUT_READY` / `DECIDED` / `EXECUTED` / `RECONCILED` / `FAILED`)
- `durationMs`

외부 어댑터 필드:

- `vendor` (`kis`, `naver`, `dart`, `yfinance`, `openclaw`, `nanobot`, `telegram`)
- `operation`
- `outcome` (`ok` / `timeout` / `error` / `empty`)
- `latencyMs`

마스킹 규칙:

- live 계좌번호: 마스킹된 형태만 노출 (`123****789`)
- 운영자 비밀번호, JWT, CSRF 토큰: 절대 출력하지 않는다.
- 브로커 API key/secret, Telegram bot token, LLM API key: 절대 출력하지 않는다.
- 예외 스택트레이스에 평문 값이 섞이지 않도록 어댑터 단에서 한 번 정리한다.

## 4. `audit_log` 정책

대상 사건:

- 인증: 로그인 성공/실패, 로그아웃, IP 차단 발생, IP 차단 수동 해제
- 운영자 편집: 템플릿/인스턴스/프롬프트/감시 종목/모델 프로필/시스템 파라미터/브로커 계좌
- 운영자 수동 조치: `auto_paused` 해제, seed 운영자 비밀번호 변경

필수 필드:

- `occurred_at`
- `actor_user_id` (인증 실패는 null 허용)
- `target_type`, `target_id`
- `action_type`
- `before_json`, `after_json` (요약 또는 변경 필드만, 민감 값 제외)
- `client_ip`
- `result` (`success` / `failure`)

보존:

- 영구 보존 대상. cold 저장 중심으로 관리한다.

## 5. `ops_event` 정책

`OpsEventEntity`는 다음 필드를 가진다.

- `occurred_at`
- `business_date` (KST 기준)
- `event_type`
- `strategy_instance_id` (nullable)
- `service_name` (`web-app` / `trading-worker` / `collector-worker` / `integrations`)
- `status_code` (`info` / `warning` / `error` / `critical`)
- `message`
- `payload_json`

대표 `event_type`:

| event_type | service_name | 의미 |
|------------|--------------|------|
| `MARKET_DATA_FETCH_FAILED` | collector-worker | KIS 현재가/분봉 조회 실패 |
| `MARKET_DATA_STALE` | collector-worker | 마지막 성공값 stale 임계 초과 |
| `NEWS_FETCH_FAILED` | collector-worker | 네이버 뉴스 또는 본문 fetch 실패 |
| `DISCLOSURE_FETCH_FAILED` | collector-worker | DART 공시 수집 실패 |
| `MACRO_FETCH_FAILED` | collector-worker | yfinance 매크로 수집 실패 |
| `WS_DISCONNECTED` | collector-worker | KIS WebSocket 끊김/재연결 실패 |
| `CYCLE_SKIPPED` | trading-worker | 락 점유, 시간대 밖, 이미 진행 중 |
| `CYCLE_FAILED` | trading-worker | 사이클 진행 중 실패 |
| `LLM_CALL_FAILED` | trading-worker | LLM subprocess 호출 실패 |
| `ORDER_SAFETY_BLOCKED` | trading-worker | 최소 안전검증 실패 |
| `RECONCILE_FAILED` | trading-worker | live 주문 상태 재조회 실패 |
| `AUTO_PAUSED` | trading-worker | 인스턴스 `auto_paused` 전환 |
| `COST_BUDGET_EXCEEDED` | trading-worker | 일일 추정 비용 상한 초과 |
| `DEPLOY_REQUESTED` | web-app | `/api/deploy` 수신 |

원칙:

- 사이클 스킵(`CYCLE_SKIPPED`)은 `trade_cycle_log`에는 남기지 않고 `ops_event`로만 남긴다.
- 외부 API 장애만으로 인스턴스를 `auto_paused`로 전환하지 않는다.
- live reconcile 실패는 예외다. `RECONCILE_FAILED` + `AUTO_PAUSED`를 함께 남기고 인스턴스를 `auto_paused(reason=reconcile_failed)`로 전환한다.

보존:

- 영구 보존 대상. 운영 화면 노출은 최근 N일(권장 30일) 기준으로 자른다.

## 6. 시스템 상태 카드

대시보드 §7.1에 노출되는 시스템 상태 카드의 입력은 `ops_event`와 직전 성공 시각 두 가지다.

판정 카테고리:

- 정상
- 지연 (warning)
- 중단 (error/critical)
- 장외 (시각 기준 거래 시간대 밖)

판정 입력 예:

| 카드 | 정상 | 지연 | 중단 |
|------|------|------|------|
| 시장 데이터 | 마지막 성공 ≤ 1분 | ≤ 5분 | > 5분 또는 연속 실패 N회 |
| 뉴스 수집 | 마지막 성공 ≤ 수집 주기 | ≤ 2배 | > 2배 또는 연속 실패 |
| 공시 수집 | 마지막 성공 ≤ 수집 주기 | ≤ 2배 | > 2배 또는 연속 실패 |
| 매크로 수집 | 마지막 성공 ≤ 1일 | ≤ 2일 | > 2일 |
| KIS WebSocket | 연결 상태 OK | 끊김 후 자동 재연결 시도 중 | 재연결 실패가 연속 N회 |
| 트레이딩 사이클 | 가장 최근 사이클 성공 | 일부 인스턴스 실패 | 전 인스턴스 실패 / `auto_paused` 다수 |

stale 처리:

- 마지막 성공값은 유지해 보여준다.
- 화면에는 stale 배지로 명시한다.
- stale 상태에서는 해당 데이터에 의존하는 신규 주문을 차단할 수 있다 (§8.2.1).

## 7. Telegram 알림 정책

전송 채널은 Telegram 하나만 사용한다(§10.9).

### 7.1 알림 발송 대상

- 시장 데이터 조회 실패 (장중에 임계 초과 시)
- 계좌 조회 실패
- 주문 API 실패
- 판단 로그 `FAILED`
- 일일 추정 비용 상한 초과
- `auto_paused(reason=reconcile_failed)`
- 활성 인스턴스의 다음 사이클 진입 자체가 불가능한 실행 오류
- 배포 이벤트 (`DEPLOY_REQUESTED` 등 운영 알림)

### 7.2 on/off와 음소거 시간대

`AlertPolicy` 기준:

- `app.alert.enabled` — 전체 on/off.
- `app.alert.quietHoursStart` / `quietHoursEnd` — KST 기준 음소거 시간대.
- `app.alert.criticalBypassesQuietHours` — `CRITICAL` 심각도는 음소거 시간대에도 전송. 기본 `true`.

규칙:

- 음소거 시간대는 자정을 넘는 구간(예: 22:00 ~ 07:00)도 허용한다.
- `enabled=false`면 어떤 알림도 발송하지 않는다.
- `CRITICAL` 외 심각도는 음소거 시간대에 발송하지 않고 다음 정상 시간대까지 누적하지 않는다 (장애가 지속되면 다시 발화).

### 7.3 심각도 매핑

`AlertEvent.Severity` 분류:

- `INFO`: 배포 알림, 정상 종료 알림 등 운영 참고
- `WARNING`: stale 데이터, 일시적 외부 API 실패, 비용 상한 임박
- `CRITICAL`: 사이클 실패 누적, reconcile 실패, `auto_paused`, 비용 상한 초과

### 7.4 실패 처리

- Telegram 전송 실패는 본업 흐름을 막지 않는다.
- 실패는 구조 로그와 `ops_event(event_type=ALERT_DELIVERY_FAILED)`로 남긴다.

## 8. health 및 readiness

### 8.1 health endpoint

- 경로: `/actuator/health`
- 응답: `200`, 기본 상태.
- 인증 없이 접근 허용.

용도:

- 컨테이너 liveness 점검.
- 단순 "프로세스 살아있음" 확인.

### 8.2 readiness endpoint

- 경로: `/actuator/health/readiness`
- 인증 없이 접근 허용.

`UP` 조건:

- PostgreSQL 연결 가능
- Redis 연결 가능
- Flyway 마이그레이션 완료
- 핵심 어댑터(설정에 따라 KIS REST, LLM 실행 파일 경로) 부팅 검증 완료

`DOWN` 시:

- reverse proxy/오케스트레이터가 트래픽을 차단한다.
- 운영자에게 즉시 알림이 가도록 `READINESS_DOWN` `ops_event`를 남긴다.

## 9. 운영 알림 정책의 비기능 기준

- 알림 본문에는 평문 secret/계좌번호 노출 금지.
- 알림은 idempotent하지 않다. 같은 사건이 반복되면 반복 전송될 수 있다.
- 큰 폭주를 막기 위해 같은 `event_type` + `instanceId` 조합은 최소 간격(예: 5분)을 두고 재발송한다 (구현 옵션).

## 10. 수동 운영 절차 (런북)

### 10.1 로그인 차단 해제

증상:

- 운영자가 특정 IP에서 로그인 실패 5회/5분 누적으로 `429`로 차단된 상태.

대응:

1. 운영자 본인 또는 관리자가 다른 인증된 세션으로 로그인한다.
2. 차단 해제 API를 호출한다.
3. Redis 키 `auth:login_block:{clientIp}`가 즉시 제거된다.
4. 해제 사건이 `audit_log(action_type=AUTH_BLOCK_RELEASED)`에 남는다.

비고:

- Redis 직접 접근으로 해제하지 말고 API를 사용한다 (감사 로그 누락 방지).

### 10.2 `auto_paused(reason=reconcile_failed)` 해제

증상:

- live 인스턴스가 다음 사이클을 시작하지 않음.
- 대시보드 카드에 "AUTO-PAUSED · reconcile_failed" 배지.

대응:

1. 직전 `RECONCILE_FAILED` `ops_event`를 확인해 실패 원인을 본다.
2. 필요 시 KIS API 상태/네트워크/시크릿을 점검한다.
3. 미확정 주문이 있으면 브로커 화면 또는 KIS API로 실제 상태를 확인한다.
4. 내부 `trade_order` 상태가 실제와 일치하도록 운영자가 데이터를 정리한다.
5. 운영자 API로 `auto_paused`를 해제한다.
6. 해제 사건이 `audit_log`와 `ops_event(AUTO_PAUSED_RELEASED)`에 남는다.

비고:

- 원인 해소 없이 해제하면 다음 사이클에서 다시 `RECONCILE_FAILED`로 돌아온다.

### 10.3 LLM subprocess 타임아웃 반복

증상:

- `trade_decision_log` 다수가 `FAILED`.
- `LLM_CALL_FAILED` `ops_event` 누적.

대응:

1. 실행 파일 경로 (`openclaw` / `nanobot`)와 권한을 확인한다.
2. 시크릿 (LLM API key) 만료 여부를 확인한다.
3. 임시로 해당 인스턴스를 `inactive`로 내린다.
4. 원인 해소 후 다시 `active`로 올린다 (활성화 검증 통과 필요).

### 10.4 수집 지연 노란불

증상:

- 시장 데이터/뉴스/공시 카드가 "지연".

대응:

1. 직전 성공 시각과 실패 누적 횟수를 본다.
2. 외부 API 상태와 네트워크를 확인한다.
3. stale 임계가 길어지면 화면에는 마지막 성공값 + stale 배지가 유지된다.
4. 일정 시간 안에 회복되지 않으면 시스템 상태가 "중단"으로 바뀌고 알림이 발화된다.

### 10.5 비용 상한 초과

증상:

- `COST_BUDGET_EXCEEDED` 알림.

대응:

1. 해당 인스턴스의 일일 추정 비용을 확인한다.
2. 필요 시 모델을 저비용 모델로 바꾸거나 입력 스펙 항목 수를 줄인다.
3. 운영 알림은 사이클 종료 후 1회 발화한다. 같은 날 반복 전송 정책은 §9 참조.

### 10.6 배포 실패

증상:

- `/api/deploy` 호출 후 readiness가 `DOWN`으로 떨어짐.

대응:

1. 컨테이너 로그에서 마이그레이션 실패/시크릿 누락/외부 의존 부팅 실패를 확인한다.
2. 이전 안정 버전으로 롤백한다 (배포 스크립트 절차).
3. `DEPLOY_FAILED` `ops_event`와 `READINESS_DOWN` `ops_event`를 함께 본다.

## 11. 시크릿과 로그

- 모든 시크릿은 GitHub Secrets → 서버 `.env`로 주입한다(§07).
- 구조 로그/`audit_log`/`ops_event`/알림 본문 어디에도 평문 시크릿을 남기지 않는다.
- 어댑터 단에서 외부 응답에 섞인 식별자도 마스킹 후에만 상위로 올린다.

## 12. 구현 체크리스트

- 구조 로그 JSON 포맷과 공통 필드 적용
- 마스킹 규칙을 한 곳에서 관리
- `audit_log` 대상 사건 누락 없이 기록
- `ops_event` `event_type` 표준화
- 시스템 상태 카드 판정 규칙 코드화
- `AlertPolicy` on/off + 음소거 시간대 + `criticalBypassesQuietHours`
- Telegram 전송 실패가 본업 흐름을 막지 않음
- `/actuator/health` + readiness 분리
- 수동 운영 절차 5종(차단 해제, auto_paused 해제, LLM 실패, 수집 지연, 비용 상한)이 API로 닫혀 있음
