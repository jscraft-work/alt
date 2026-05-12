# 10. 테스트 전략

## 1. 문서 목적과 범위

이 문서는 [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md)와 `docs/01 ~ 09`를 기준으로 ALT-Web의 테스트 층, 도구, 책임 경계를 고정한다.

목표:

- 테스트 층(layer)별 범위와 책임 고정
- 외부 의존 처리 원칙(Fake/WireMock/실연동 금지) 고정
- DB/Redis/시간 처리 규칙 고정
- 수동 검증으로만 닫히는 항목 명확화
- CI 게이트 기준 고정

비범위:

- 성능/부하 시험 시나리오 상세
- E2E 브라우저 자동화 도구 선정
- 카오스 엔지니어링 설계

## 2. 테스트 층

| 층 | 책임 | 도구 |
|----|------|------|
| 단위 | 순수 로직, 도메인 규칙, 헬퍼 | JUnit 5 |
| 리포지토리 | JPA 매핑, 제약, partial unique, soft delete, optimistic lock | Spring `@DataJpaTest` + Testcontainers PostgreSQL |
| API 통합 | 컨트롤러, 보안, 시리얼라이저, CSRF, optimistic lock 응답 | `@SpringBootTest` + MockMvc |
| 인증/보안 통합 | 로그인, 세션 만료, sliding 재발급, CSRF, 차단/해제 | `@SpringBootTest` + MockMvc + Redis 컨테이너 |
| 스케줄러/사이클 | 활성 인스턴스 선택, 락, 스냅샷, 시간대 판정 | `@SpringBootTest` + `MutableClock` + Fake gateway |
| 어댑터 계약 | 외부 응답 → 도메인 매핑, 실패 분류 | WireMock 또는 Fake 구현체 |
| LLM subprocess | `ProcessBuilder` 어댑터, 타임아웃/exit/stderr 분류 | 단위 테스트 + 가짜 실행 파일/스크립트 |
| E2E 스모크 | paper 사이클 한 바퀴, 핵심 API 응답 | `@SpringBootTest` + Fake gateway 묶음 |

층의 경계는 다음과 같이 지킨다.

- 단위는 Spring context를 띄우지 않는다.
- 리포지토리는 컨트롤러/서비스 호출을 섞지 않는다.
- API 통합은 어댑터 실제 구현체 대신 Fake/WireMock을 주입한다.
- E2E 스모크는 외부 시스템과 통신하지 않는다.

## 3. 외부 의존 처리 원칙

다음 외부 의존은 **테스트에서 실제로 호출하지 않는다**.

- KIS REST / WebSocket
- 네이버 검색/원문
- DART 공시
- Yahoo Finance (`yfinance`)
- LLM subprocess (`openclaw`, `nanobot`)
- Telegram Bot API
- GitHub 배포 webhook 외부 발신

대체 수단:

- 도메인 포트(`BrokerGateway`, `MarketDataGateway`, `NewsGateway`, `DisclosureGateway`, `MacroGateway`, `TradingDecisionEngine`, `AlertGateway`)는 모두 Fake 구현체를 제공한다.
- 어댑터 계약 테스트는 WireMock으로 벤더 응답을 그대로 흉내 낸다.
- LLM subprocess 어댑터는 가짜 실행 파일이나 echo 스크립트를 사용한다.

원칙:

- 같은 사이클 안에서 재시도가 금지된 호출(주문 실행, LLM 호출)은 테스트도 같은 규칙으로 검증한다.
- 실서버/실 brokerage 호출은 CI에서 절대 발생하지 않는다.

## 4. DB와 마이그레이션

- DB는 Testcontainers PostgreSQL 위에서만 검증한다.
- 임의 in-memory DB(H2 등)는 사용하지 않는다.
- Flyway는 빈 DB에 끝까지 적용된 상태를 검증 대상으로 본다.
- 마이그레이션 V1, V2, V3, V4가 누적해서 적용된 결과를 회귀 검증한다 (`TradingSchemaFlywayTest` 등).

DB 정리 규칙:

- 테스트 클래스 또는 `@BeforeEach`에서 운영 도메인 테이블만 비운다.
- `flyway_schema_history`와 시스템 시드는 비우지 않는다.
- Testcontainers 컨테이너는 가능한 한 재사용해 시작 시간을 줄인다.

## 5. Redis와 시간 처리

Redis:

- 통합 테스트는 Testcontainers Redis 컨테이너를 사용한다.
- 테스트 시작 시 사용한 키 네임스페이스만 비운다 (`auth:*`, `trading:lock:*`, `market:orderbook:*` 등).
- 운영 환경의 다른 키를 가정하지 않는다.

시간 처리:

- 사이클/세션 시간 관련 테스트는 `MutableClock`을 주입해 결정적 시각을 사용한다.
- 시스템 시간 의존 코드는 `Clock` 빈을 통해서만 시간을 읽는다.
- "지금부터 N초 뒤" 같은 sleep 기반 검증은 사용하지 않는다.

KST 고정:

- 응답 직렬화는 `Asia/Seoul`로 검증한다 (`DashboardTimeZoneSerializationTest`).

## 6. 인증/보안 테스트 기준

- 인증 통합 테스트는 MockMvc로 쿠키와 헤더를 직접 검증한다 (`AuthLoginApiTest`, `CsrfProtectionTest`).
- sliding 재발급은 `MutableClock`으로 임계 시각을 재현한다 (`SlidingSessionTest`).
- absolute max session 초과는 7일 시간 점프로 검증한다 (`AbsoluteSessionExpiryTest`).
- 로그인 실패 차단/해제는 Redis 키 상태와 응답 코드를 함께 검증한다.
- reverse proxy 신뢰 체인은 케이스별 단위 테스트로 검증한다 (`TrustedProxyIpResolverTest`).

## 7. 사이클/주문 테스트 기준

- 활성 인스턴스 선택은 fixture 인스턴스 N개로 검증한다 (`ActiveInstanceSchedulerTest`).
- 같은 인스턴스 중복 실행은 Redis 락 충돌로 검증한다 (`RedisLockSerialExecutionTest`).
- 사이클 시작 후 설정 변경이 현재 사이클에 영향 없음을 직접 검증한다 (`SettingsSnapshotFreezeTest`).
- LLM subprocess 어댑터는 정상/타임아웃/non-zero exit/stderr 인증오류/빈 응답을 모두 분류한다.
- `paper` end-to-end는 판단 → 주문안 → 주문 → 포트폴리오 갱신까지 한 사이클 안에 검증한다 (`PaperTradingCycleE2ETest`).
- `live` reconcile 실패 시 `auto_paused(reason=reconcile_failed)` 전환을 검증한다.

## 8. 어댑터 계약 테스트

대상:

- KIS 계좌/주문/주문상태/현재가/분봉
- 네이버 뉴스 목록과 원문
- DART 공시
- yfinance 매크로
- Telegram 발송

검증 항목:

- 정상 응답 → 도메인 모델 매핑
- 인증 실패, 요청 형식 오류, 일시 장애, 타임아웃, 응답 형식 오류, 빈 응답의 6가지 분류
- 마스킹 정책 (계좌번호 등)

비고:

- 실제 벤더 SDK가 있다면 SDK 메서드 단에서 mocking하지 말고 HTTP 응답 레이어에서 WireMock으로 흉내 낸다.
- subprocess 계열은 echo/cat 등 운영체제 기본 명령으로 응답 시뮬레이션을 만든다.

## 9. 실패 경로 회귀 테스트

다음 실패 경로는 회귀 테스트로 반드시 닫아둔다 (`FailurePathRegressionTest`).

- 로그인 실패 5회 → `429`
- CSRF 누락 → `403`
- absolute session 초과 → `401`
- optimistic lock 충돌 → `409` + `OPTIMISTIC_LOCK_CONFLICT`
- 활성화 검증 실패 → `422` + `INSTANCE_NOT_ACTIVATABLE`
- live 계좌 중복 사용 → `409` + `LIVE_BROKER_ACCOUNT_IN_USE`
- 시장 데이터 fetch 실패 → `MARKET_DATA_FETCH_FAILED` `ops_event`
- LLM 호출 실패 → `trade_decision_log=FAILED`, `trade_cycle_log=FAILED`
- 최소 안전검증 실패 → 주문 미발행 + `ORDER_SAFETY_BLOCKED` `ops_event`
- reconcile 실패 → `auto_paused(reason=reconcile_failed)`

## 10. 수동 검증으로만 닫히는 항목

자동 테스트만으로 닫지 않는 항목:

- **task 16 수동 완료 조건**: KIS sandbox 또는 실계좌 환경에서 최소 1회 주문 제출과 상태 조회를 검증하고, 결과(요청/응답 요약, 발화된 `ops_event`)를 별도 운영 문서에 남긴다.
- **task 17 수동 완료 조건**: 운영 배포 문서와 시크릿 주입 절차를 실제 서버 기준으로 1회 검증하고, 절차/스크린샷/`/api/deploy` 응답을 별도 운영 문서에 남긴다.
- Telegram 실제 chat 발송: 운영 chat ID로 한 번 발화시켜 본문/마스킹/음소거 동작을 사람 눈으로 확인한다.
- 운영자 최초 계정 생성: seed 절차가 실제 서버 환경에서 한 번 동작하는 것을 확인한다.

원칙:

- 수동 검증 결과는 PR 또는 운영 문서로 남긴다.
- 수동 검증 누락 상태로 task 16/17은 "완료"로 표시하지 않는다.

## 11. CI 게이트

- PR 단계: 단위 + 리포지토리 + API 통합 + 인증/보안 + 어댑터 계약 + 스케줄러/사이클 + E2E 스모크 (전 17 task의 필수 테스트 묶음).
- 야간 또는 릴리즈 단계: 전체 `./gradlew test` + 마이그레이션 누적 재검증.
- 외부 실연동 테스트는 CI에서 실행하지 않는다.

테스트 결과는 `build/test-results/test/*.xml` + HTML 리포트로 보관하고, 실패 시 PR 머지를 차단한다.

## 12. 테스트 데이터/시드

- 운영 seed는 단위/리포지토리 테스트에서 의존하지 않는다.
- 통합 테스트는 각 테스트 클래스가 필요한 fixture를 명시적으로 만들어 사용한다.
- 공유 fixture는 `*IntegrationTestSupport` 베이스 클래스에 모은다.
- `app_user` 생성, 로그인 쿠키 획득 같은 공통 절차는 헬퍼로 캡슐화한다.

## 13. 구현 체크리스트

- 단위/리포지토리/API 통합/스케줄러/어댑터/E2E 층 분리
- Testcontainers PostgreSQL과 Redis 사용, in-memory DB 금지
- `MutableClock` 기반 시간 결정성 확보
- 6가지 외부 실패 분류 어댑터 계약 테스트
- LLM subprocess 어댑터 timeout/exit/stderr 분류 테스트
- `paper` end-to-end 스모크 통과
- `live` reconcile 실패 시 `auto_paused` 회귀 테스트 통과
- 실패 경로 회귀 테스트 (`FailurePathRegressionTest`) 통과
- 외부 실연동은 CI에서 실행되지 않음
- task 16/17의 수동 검증 결과를 운영 문서로 남김
