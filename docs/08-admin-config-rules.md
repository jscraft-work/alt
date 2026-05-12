# 08. 운영 설정 설계

## 1. 문서 목적과 범위

이 문서는 [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md) §7.6, §10.2, §11과 [02-domain-model.md](/Users/jongsoobae/workspace/alt-java/docs/02-domain-model.md), [04-api-spec.md](/Users/jongsoobae/workspace/alt-java/docs/04-api-spec.md)를 기준으로 운영자 설정 화면/API의 편집 규칙을 구현 가능한 수준으로 고정한다.

목표:

- 템플릿/인스턴스 복제 규칙 고정
- 상태별 수정 가능 범위 고정
- 활성화 전 검증 6항목의 판정 기준 고정
- optimistic lock 충돌 처리 규칙 고정
- 설정 변경의 사이클 반영 시점 고정
- 프롬프트 버전/감시 종목/계좌 중복 사용 규칙 고정

비범위:

- 설정 화면의 시각 디자인
- API 라우트 상세 (별도 `04-api-spec.md` 참조)
- 트레이딩 사이클 내부 동작 (별도 `05-trading-cycle-design.md` 참조)

## 2. 공통 원칙

- 설정 변경은 항상 운영자 인증을 요구한다(§06).
- 설정 저장은 `version` 기반 optimistic lock을 적용한다.
- 저장된 변경은 **진행 중 사이클에는 반영하지 않고**, 다음 사이클부터 반영한다.
- 진행 중 사이클은 시작 시점의 설정 스냅샷(§13)을 그대로 사용한다.
- 모든 편집은 `audit_log`에 actor / target / before-after 요약과 함께 남긴다.
- 같은 항목을 동시에 수정한 경우 충돌은 사용자에게 명시적으로 노출하고, 클라이언트가 최신 상태를 다시 불러와 재시도하게 한다.

## 3. 기준정보 vs 인스턴스 설정 경계

기준정보(템플릿/모델 프로필/시스템 파라미터/종목 마스터/브로커 계좌)는 인스턴스보다 먼저 만든다.

기준정보 편집 정책:

| 대상 | 생성 | 수정 | 삭제 |
|------|------|------|------|
| 전략 템플릿 | 허용 | 허용 (active 인스턴스에 자동 전파되지 않음) | soft delete |
| LLM 모델 프로필 | 허용 | 허용 | soft delete (사용 중이면 차단) |
| 시스템 파라미터 | seed로 생성, 수정만 허용 | 허용 | 금지 |
| 종목 마스터 | 허용 | 허용 | 비활성/숨김만 허용 (하드 삭제 금지) |
| 브로커 계좌 | 허용 | 허용 | active live 인스턴스가 사용 중이면 차단 |

비고:

- 종목 마스터는 글로벌 자산이라 하드 삭제 대신 비활성/숨김으로만 닫는다.
- 시스템 파라미터의 "기본값 초기화"는 글로벌 운영 설정 탭의 묶음만 적용한다.

## 4. 인스턴스 lifecycle과 수정 가능 범위

lifecycle 주상태는 `draft / active / inactive` 세 가지다(§10.2). `auto_paused`는 `active` 위에 얹히는 운영 메타 상태다.

### 4.1 상태별 편집 가능 항목

| 항목 | draft | active | active + auto_paused | inactive |
|------|-------|--------|----------------------|----------|
| 인스턴스명 | 가능 | 불가 | 불가 | 가능 |
| 실행 모드(`paper / live`) | 가능 | 불가 | 불가 | 가능 |
| 연결 계좌 | 가능 | 불가 | 불가 | 가능 |
| 전략 예산 | 가능 | 불가 | 불가 | 불가 |
| 프롬프트(신규 버전) | 가능 | 가능(다음 사이클부터) | 가능 | 가능 |
| 입력 스펙 | 가능 | 가능 | 가능 | 가능 |
| 감시 종목 | 가능 | 가능 | 가능 | 가능 |
| 트레이딩 모델 | 가능 | 가능 | 가능 | 가능 |
| 인스턴스 시스템 파라미터 | 가능 | 가능 | 가능 | 가능 |
| 실행 주기 | 가능 | 가능 | 가능 | 가능 |

규칙:

- 상태별로 막힌 필드를 수정 요청하면 `INSTANCE_FIELD_LOCKED`로 거절한다.
- `active` 상태의 편집은 모두 다음 사이클부터 반영한다.
- `auto_paused`도 편집 가능 범위는 `active`와 동일하다(스케줄러만 다음 사이클을 시작하지 않을 뿐이다).

### 4.2 상태 전이

허용 전이:

- `draft ↔ active ↔ inactive` 전이는 모두 허용한다.
- `draft → active`, `inactive → active`는 §5의 활성화 검증을 통과한 경우에만 허용한다.
- `inactive → active`는 reset이 아니라 resume이다. 현금/보유/손익은 초기화하지 않는다.

`auto_paused`:

- `auto_paused_reason`은 `reconcile_failed`만 허용한다.
- `auto_paused`는 운영자가 명시적으로 해제하지 않으면 새 사이클을 시작하지 않는다.
- `draft / inactive`로 전이될 때 `auto_paused_reason`은 즉시 비운다.

## 5. 활성화 전 검증

`draft → active` 또는 `inactive → active` 전이 직전에 아래 6항목을 검증한다(§10.2).

활성화 검증 항목:

1. 유효한 트레이딩 판단 프롬프트가 결정되어 있을 것
2. 유효한 트레이딩 모델이 결정되어 있을 것
3. 유효한 전략 입력 스펙이 결정되어 있을 것
4. 유효한 인스턴스 실행 주기가 결정되어 있을 것
5. 전략 예산이 설정되어 있을 것
6. 실행 모드가 `live`라면 연결 계좌가 선택되어 있을 것
7. 평가 대상 범위가 `full_watchlist`라면 감시 종목이 1개 이상일 것

유효값 판정 규칙:

- 각 항목의 값은 **인스턴스 개별 설정**과 **템플릿 기본값** 중 실행 시 실제로 적용될 값으로 본다.
- 인스턴스 개별 값이 비어 있으면 템플릿 기본값을 사용한다.
- 둘 다 비어 있으면 미충족으로 판정한다.

응답 규칙:

- 검증 실패는 `INSTANCE_NOT_ACTIVATABLE` 에러로 응답한다.
- `meta.missing[]`에 미충족 항목 코드를 모두 담는다 (`prompt`, `model`, `inputSpec`, `cycleMinutes`, `budget`, `account`, `watchlist`).
- 부분 활성화는 없다. 한 항목이라도 미충족이면 `active` 전이를 거절한다.

## 6. live 모드 계좌 중복 사용 방지

규칙:

- 동일 `broker_account_id`를 같은 시점에 두 개 이상의 `active live` 인스턴스가 사용할 수 없다.
- `auto_paused` 상태도 활성 인스턴스로 간주해 계좌를 점유한 것으로 본다.

검사 시점:

- `draft → active` 전이 시점에 검사한다.
- `inactive → active` 전이 시점에도 검사한다.
- `paper → live` 모드 변경 시점에 검사한다.

응답 규칙:

- 충돌 시 `LIVE_BROKER_ACCOUNT_IN_USE`로 거절한다.
- `meta.conflictingInstanceId`에 점유 중인 인스턴스 ID를 노출한다.

## 7. 복제 정책

### 7.1 인스턴스 복제

- 복제 대상은 **설정만**이다.
- 복제되는 값: 프롬프트, 전략 입력 스펙, 감시 종목, 모델, 시스템 파라미터, 예산, 모드, 연결 계좌
- 복제되지 않는 값: 자산, 보유, 판단 로그, 주문 이력, 사이클 로그, `auto_paused` 상태
- 복제 결과는 항상 `draft`로 시작한다.
- 같은 템플릿으로 인스턴스를 여러 개 만들 수 있다.

### 7.2 템플릿 복제

- 운영자는 기존 템플릿을 복제해 새 템플릿을 시작할 수 있다.
- 템플릿 기본 프롬프트 수정은 **신규 인스턴스에만** 적용된다. 기존 인스턴스에는 자동 전파되지 않는다.

## 8. 프롬프트 버전 관리

- 인스턴스 프롬프트 버전 이력은 **인스턴스별로 독립**이다.
- 프롬프트 저장은 새로운 `strategy_instance_prompt_version` row를 생성한다.
- 현재 버전 전환은 인스턴스의 `current_prompt_version_id`를 업데이트한다.

버전 번호 규칙:

- 신규 버전 저장 시 해당 인스턴스의 가장 큰 `version_no + 1`을 사용한다.
- 과거 버전 복원도 새 버전으로 다시 저장한다 (in-place 복원은 없다).

저장 전 경고:

- UI는 활성 인스턴스의 프롬프트 저장 시 "다음 사이클부터 반영됩니다" 안내를 표시한다.

## 9. 감시 종목 관리

- 글로벌 종목 마스터를 참조한 N:M 매핑이다.
- 매핑 삭제는 soft delete만 허용한다.
- soft delete된 종목을 다시 추가하는 것은 허용한다.
- 추가는 항상 새 row를 만든다 (재활성화가 아니라 신규 생성).
- 감시 종목 수 자체에는 시스템 상한이 없다 (입력 스펙의 항목 수 제한은 별도).

unique 규칙:

- `(strategy_instance_id, asset_master_id) WHERE deleted_at IS NULL` partial unique.
- soft delete 후 동일 종목 재추가는 partial unique를 위반하지 않는다.

`dart_corp_code`:

- 종목 마스터의 `dart_corp_code`는 자동 lookup이 기본이다.
- lookup 결과가 모호하거나 실패한 경우에만 운영자가 수동 입력/검색하도록 UX를 제공한다.

## 10. optimistic lock 충돌 처리

대상:

- 모든 운영자 편집 API (`PATCH /api/admin/**`).

요청 규칙:

- 클라이언트는 마지막으로 조회한 row의 `version`을 본문에 포함해 보낸다.
- 서버는 DB의 현재 `version`과 일치하지 않으면 충돌로 본다.

응답 규칙:

- 상태 코드: `409 Conflict`
- 에러 코드: `OPTIMISTIC_LOCK_CONFLICT`
- 응답 `meta.currentVersion`에 서버의 현재 `version` 값을 담아준다.

클라이언트 권장 동작:

- 서버 응답으로 화면을 다시 불러온다.
- 사용자에게 "다른 운영자가 먼저 저장했습니다" 안내를 노출한다.
- 사용자 의도를 확인한 뒤 재시도한다.

비고:

- 새 row 생성(`POST`)에는 optimistic lock을 적용하지 않는다.
- soft delete(`DELETE`) 호출에도 `version`을 요구한다.

## 11. 감사 로그 기준

`audit_log`에 남기는 운영자 편집 이벤트:

- 전략 템플릿 생성/수정/삭제
- 전략 인스턴스 생성/수정/상태 전이/삭제/복제
- 프롬프트 버전 저장/현재 버전 전환
- 감시 종목 추가/삭제
- 종목 마스터 생성/수정/비활성
- LLM 모델 프로필 생성/수정/삭제
- 시스템 파라미터 수정
- 브로커 계좌 생성/수정/연결/해제
- 운영자 차단 IP 수동 해제
- `auto_paused` 수동 해제

최소 필드:

- `occurred_at`
- `actor_user_id`
- `target_type`, `target_id`
- `action_type`
- `before_json`, `after_json` (요약 또는 변경 필드만)
- `client_ip`

비고:

- 평문 비밀번호, JWT, 브로커 API key 같은 민감 값은 `before_json` / `after_json`에 남기지 않는다.
- live 계좌번호는 마스킹된 형태로만 기록한다.

## 12. live 계좌 식별자 마스킹

- DB 저장 값은 원본 그대로 둔다.
- API 응답, UI 표시, 감사 로그, 알림 본문에서는 마스킹된 식별자만 노출한다.
- 마스킹 규칙은 한 곳(`BrokerAccountMasker` 등 어댑터)에서 정의한다.

## 13. 설정 스냅샷과 사이클 반영 시점

- 사이클 시작 시점에 인스턴스가 사용할 설정을 한 번 읽어 스냅샷으로 고정한다.
- 스냅샷 대상: 프롬프트(현재 버전), 모델, 입력 스펙, 실행 주기, 예산, 감시 종목, 인스턴스 단위 시스템 파라미터.
- 사이클 진행 중 운영자가 어떤 설정을 바꿔도 그 사이클에는 반영되지 않는다.
- 변경은 다음 사이클의 시작 시점에 다시 스냅샷을 잡을 때 반영된다.

비고:

- 이 규칙은 운영자 편집 UX의 "다음 사이클부터 반영" 문구와 일관된다.
- 스냅샷 상세는 `13-trading-cycle-scheduler.md` task와 `05-trading-cycle-design.md`에서 다룬다.

## 14. 응답과 에러 코드 정리

운영 설정 API에서 사용하는 공통 에러 코드:

| 코드 | HTTP | 설명 |
|------|------|------|
| `OPTIMISTIC_LOCK_CONFLICT` | 409 | `version` 불일치 |
| `INSTANCE_FIELD_LOCKED` | 422 | 상태가 해당 필드 수정을 허용하지 않음 |
| `INSTANCE_NOT_ACTIVATABLE` | 422 | 활성화 전 검증 6항목 중 일부 미충족 |
| `LIVE_BROKER_ACCOUNT_IN_USE` | 409 | 다른 active live 인스턴스가 계좌 점유 |
| `STRATEGY_TEMPLATE_IN_USE` | 409 | 활성 인스턴스가 참조 중인 템플릿을 삭제 시도 |
| `ASSET_MASTER_IN_USE` | 409 | 활성 감시 종목으로 참조 중인 종목을 하드 삭제 시도 |
| `WATCHLIST_DUPLICATE` | 409 | 활성 매핑이 이미 존재 |

비고:

- 422는 검증 실패, 409는 동시성/리소스 충돌로 구분한다.
- 메시지는 운영자 화면에 그대로 노출 가능한 한국어 문구로 정리한다.

## 15. 구현 체크리스트

- 상태별 편집 가능 항목 가드
- 활성화 검증 6항목과 `INSTANCE_NOT_ACTIVATABLE` 응답
- `LIVE_BROKER_ACCOUNT_IN_USE` 충돌 검사
- 인스턴스/템플릿 복제 (설정만 복제, 상태 = `draft`)
- 프롬프트 신규 버전 저장과 현재 버전 전환
- 과거 버전 복원은 새 버전으로 저장
- 감시 종목 partial unique와 재추가 허용
- 모든 편집 API에 `version` 기반 optimistic lock
- 운영 편집 감사 로그 기록
- live 계좌 식별자 마스킹 일관성
- 사이클 시작 시 설정 스냅샷 고정
