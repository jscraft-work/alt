# 01. System Architecture

## 1. 문서 목적과 범위

이 문서는 `spec2.md`를 구현으로 옮기기 위한 1차 아키텍처 기준선이다. 현재 저장소의 최소 Java/Gradle 상태를 설명하는 문서가 아니라, 향후 Java/Spring Boot 기반 ALT-Web을 어떤 경계와 런타임 구조로 구현할지 정하는 데 목적이 있다.

포함 범위:

- 운영 웹/API
- 주기형 트레이딩 사이클
- 시장/뉴스/공시 수집
- LLM 기반 트레이딩 판단
- 영속성, 인증, 관측성, 외부 연동 경계

비범위:

- 브로커 API 세부 프로토콜
- 개별 SQL 스키마 상세
- 프론트엔드 컴포넌트 설계
- 세금/정산 공식

## 2. 아키텍처 목표

- `spec2.md`의 주기형 전략 운영 원칙을 훼손하지 않는다.
- 웹 요청 처리와 백그라운드 실행을 분리해 장애 전파를 줄인다.
- 전략 인스턴스별 상태, 자산, 판단, 주문 이력을 강하게 분리한다.
- 설정 변경은 즉시 저장하되, 실행 반영은 다음 사이클부터만 허용한다.
- `paper`/`live` 차이는 주문 실행 어댑터에서 흡수하고 상위 사이클 구조는 동일하게 유지한다.
- 새 전략은 코드 추가보다 템플릿/인스턴스 설정 복제로 시작할 수 있어야 한다.

## 3. 상위 구조

기본 배치는 단일 저장소의 Spring Boot 모놀리스를 전제로 하되, 런타임 역할은 내부적으로 분리한다.

- `web-app`
  - 공개 조회 API, 운영자 API, 로그인 제공
- `trading-worker`
  - 전략 인스턴스별 주기 스케줄링, 사이클 실행, reconcile, 주문 실행 담당
- `collector-worker`
  - 시장 스냅샷, 분봉, 뉴스, DART 공시, 매크로 데이터 수집 담당
- `shared-db`
  - 운영 데이터의 단일 영속 저장소
- `shared-cache`
  - 단기 시세/호가 캐시, rate limit, 로그인 실패 카운트, 분산 락 용도

배포 단위는 처음부터 분리한다.

- `web-app`
- `trading-worker`
- `collector-worker`

각 배포 단위는 같은 코드베이스를 공유하되, 역할별 프로파일과 실행 진입점은 분리한다.
`web-app`은 수평 확장을 허용하고, `collector-worker`는 단일 인스턴스로 운영한다. `trading-worker`의 다중 인스턴스 운영은 현재 버전 범위에 포함하지 않는다.

## 4. 컴포넌트 경계

### 4.1 API / UI 경계

- 공개 조회 API
  - 대시보드, 매매이력, 뉴스/공시, 차트 조회
- 운영자 API
  - 로그인 필요, 전략 템플릿/인스턴스/프롬프트/모델/파라미터 변경
- 내부 운영 API
  - 워커 상태 조회, 차단 해제, 수동 재동기화 같은 운영성 기능

### 4.2 도메인 경계

- `strategy`
  - 템플릿, 인스턴스, lifecycle, 감시종목, 판단 시점 설정값
- `trading`
  - 사이클 실행, 판단 입력 조립, LLM 판단 호출, 주문 변환, reconcile, auto-paused 판정
- `marketdata`
  - 시세 스냅샷, 분봉, 호가 캐시
- `news`
  - 뉴스, 요약, 유용성 판정, 외부 링크 메타
- `disclosure`
  - 공시, 기업코드 매핑, 공시 조회 모델
- `macro`
  - 환율/해외지수/금리 등 매크로 스냅샷
- `portfolio`
  - 자산, 보유, 실현손익/평가손익 계산
- `llm`
  - 트레이딩 판단용 LLM 실행기
- `auth`
  - 운영자 로그인, JWT, 로그인 실패 차단
- `ops`
  - audit log, 운영 이벤트 로그, 메트릭, 헬스 상태

### 4.3 외부 시스템 경계

- 브로커 API
  - live 주문, 계좌 조회, 주문 상태 조회
- 시장 데이터 공급자
  - 현재가/스냅샷, 분봉, 웹소켓
- 뉴스 공급자
- DART 공시 API
- LLM API

외부 연동은 모두 포트/어댑터로 감싼다. 도메인 서비스는 특정 벤더 SDK나 응답 형식에 직접 의존하지 않는다.

### 4.4 워커/파이프라인 경계

- `collector`
  - `marketdata`, `news`, `disclosure`, `macro` 도메인에 데이터를 적재하는 수집 워커/파이프라인 orchestration
- `interfaces.scheduler`
  - 스케줄 트리거 진입점
- `trading.application.cycle`
  - 트레이딩 사이클 실행 orchestration

## 5. 런타임 프로세스

### 5.1 웹 프로세스

- HTTP 요청 처리
- JWT 인증/인가
- 조회 API 응답
- 설정 저장

웹 프로세스는 트레이딩 사이클을 직접 실행하지 않는다. 운영자가 설정을 바꿔도 현재 진행 중 사이클을 건드리지 않고 DB에만 반영한다.

### 5.2 트레이딩 워커

- 활성 인스턴스 목록 주기 조회
- 인스턴스별 스케줄 등록
- 사이클 시작 전 reconcile
- 전략 입력 스펙 기반 입력 데이터 조회 및 판단 시점 설정값 확정
- LLM 판단 호출
- 판단 결과를 최소 안전검증 후 주문 의사결정으로 변환
- `paper`/`live` 어댑터를 통한 주문 실행
- 사이클 실행 로그, 판단 로그 및 주문 이력 기록

### 5.3 수집 워커

- 장중/장외 정책에 맞는 시세 수집
- 웹소켓 재연결 및 백필
- 뉴스 수집/요약
- DART 공시 수집
- 장중 호가 원본의 Redis TTL 저장
- 일일 정리 작업 및 보존 정책 적용

## 5.4 시간 표준

- 서버, 배치, 스케줄러, 로그 기준 timezone은 `Asia/Seoul`로 고정한다.
- 문서와 API에서 "오늘"은 KST 기준 일자를 의미한다.
- 사용자 표시 시각과 운영 집계 시각은 모두 KST 기준으로 계산한다.

## 6. 모듈 및 패키지 구조 제안

초기에는 단일 Gradle 프로젝트로 시작하되 패키지 경계를 먼저 강하게 잡는다.
최상위 구조는 `package-by-feature`를 기본으로 하되, inbound와 outbound를 분리한다.

- `interfaces`
  - 우리 시스템의 진입점(inbound)
- `integrations`
  - 외부 시스템 호출(outbound)

도메인 기능은 `strategy`, `trading`, `marketdata`, `news`, `disclosure`, `macro` 같은 기능 패키지에 두고, 각 기능 내부에서만 `application / domain / infrastructure`를 사용한다.

```text
work.jscraft.alt
├─ AltApplication
├─ common
│  ├─ config
│  ├─ time
│  ├─ json
│  ├─ error
│  └─ persistence
├─ auth
│  ├─ application
│  ├─ domain
│  └─ infrastructure
├─ strategy
│  ├─ application
│  ├─ domain
│  └─ infrastructure
├─ trading
│  ├─ application
│  │  ├─ cycle
│  │  ├─ inputspec
│  │  └─ decision
│  ├─ domain
│  │  ├─ cycle
│  │  ├─ decision
│  │  ├─ order
│  │  └─ reconcile
│  └─ infrastructure
├─ portfolio
│  ├─ application
│  ├─ domain
│  └─ infrastructure
├─ marketdata
│  ├─ application
│  ├─ domain
│  └─ infrastructure
├─ news
│  ├─ application
│  ├─ domain
│  └─ infrastructure
├─ disclosure
│  ├─ application
│  ├─ domain
│  └─ infrastructure
├─ macro
│  ├─ application
│  ├─ domain
│  └─ infrastructure
├─ llm
│  ├─ application
│  └─ infrastructure
├─ insight
│  ├─ application
│  ├─ domain
│  └─ infrastructure
├─ ops
│  ├─ application
│  ├─ domain
│  └─ infrastructure
├─ collector
│  ├─ application
│  │  ├─ marketdata
│  │  ├─ news
│  │  ├─ disclosure
│  │  └─ macro
│  └─ infrastructure
├─ interfaces
│  ├─ api
│  │  ├─ publicapi
│  │  ├─ admin
│  │  └─ internal
│  ├─ scheduler
│  └─ webhook
└─ integrations
   ├─ kis
   │  ├─ auth
   │  ├─ broker
   │  ├─ marketdata
   │  └─ websocket
   ├─ navernews
   ├─ dart
   ├─ gemini
   ├─ openclaw
   ├─ nanobot
   ├─ telegram
   └─ yfinance
```

패키지 규칙:

- 최상위 패키지는 기술 레이어가 아니라 기능/운영 책임 기준으로 자른다.
- 각 기능 내부에서만 `application`, `domain`, `infrastructure`를 사용한다.
- `interfaces`는 inbound adapter만 둔다.
- `interfaces.api`는 HTTP API, `interfaces.scheduler`는 스케줄 트리거, `interfaces.webhook`은 외부 서명 요청 진입점이다.
- `integrations`는 outbound adapter만 둔다.
- KIS, 네이버 뉴스, DART, Telegram 같은 벤더별 클라이언트는 `integrations` 아래에 모은다.
- 각 도메인은 벤더 클라이언트를 직접 참조하지 않고 `BrokerGateway`, `MarketDataGateway`, `NewsGateway`, `TradingDecisionEngine` 같은 포트(interface)만 참조한다.
- 조회 전용 query service와 read model은 각 도메인 `application.query` 아래에 둔다.
- 전략은 프롬프트와 별도로 `input spec`을 가지며, `trading.application.inputspec`이 데이터 조회 범위를 해석한다.
- 판단 대상 범위는 v1에서 `held_only`와 `full_watchlist` 두 모드만 공식 지원한다.
- 종목별 인사이트/know-how는 `insight` 도메인에 두고, 전략이 opt-in한 경우에만 프롬프트에 주입한다.
- 기능 내부 `infrastructure`는 주로 영속성 구현, 도메인별 adapter, mapper 같은 내부 인프라에 사용한다.
- JPA 엔티티와 API DTO를 직접 공유하지 않는다.
- `marketdata`, `news`, `disclosure`, `macro`는 최상위 데이터 도메인이다.
- `collector`는 이 도메인들을 읽고 쓰는 수집 워커/파이프라인이며, 소비자가 의존할 대상이 아니다.
- `trading`, 조회 API는 `collector`가 아니라 `marketdata`, `news`, `disclosure`, `macro` 도메인 포트에 의존한다.

이 구조를 권장하는 이유:

- Spring Boot에서 흔한 전역 `controller / service / repository` 구조보다 기능 응집도가 높다.
- inbound 진입점은 한곳에 모여 탐색성과 운영 가시성이 좋다.
- outbound 연동은 벤더별로 모여 인증, 공통 DTO, 재시도 정책을 재사용하기 쉽다.
- 데이터 도메인과 수집 워커를 분리해 소비자(`trading`, 조회 API)가 파이프라인 구현에 의존하지 않게 한다.
- 수집 파이프라인 코드는 `collector`에 모아 워커 관점에서 추적하기 쉽다.
- `trading`은 핵심 비즈니스 모듈로 분리해 판단/주문/정합성 로직을 집중시킬 수 있다.
- 도메인이 외부 벤더 세부사항에 오염되지 않아 테스트와 교체가 쉬워진다.
- 이후 Spring Modulith 같은 모듈 검증 도구를 붙이기에도 자연스럽다.

규모가 커지면 Gradle 멀티모듈로 다음 순서로 분리한다.

- `app-web`
- `app-worker-trading`
- `app-worker-collector`
- `module-domain-*`
- `module-integration-*`

## 7. 요청 및 데이터 흐름

### 7.1 조회 요청 흐름

1. 클라이언트가 공개 조회 API 호출
2. API 계층이 인스턴스 범위와 필터를 해석
3. 조회 전용 서비스가 DB/캐시에서 읽기
4. 응답 DTO로 변환 후 반환

조회 API는 쓰기 모델과 분리된 읽기 쿼리를 사용한다. 대시보드처럼 조인 비용이 큰 화면은 전용 read model 또는 집계 테이블을 허용한다.

### 7.2 설정 변경 흐름

1. 운영자 인증 확인
2. 요청 검증 및 lifecycle 규칙 검사
3. 설정 저장
4. audit log 기록
5. 워커는 다음 주기 poll에서 변경 감지
6. 다음 사이클 시작 시 새 설정값 적용

핵심 규칙은 "저장은 즉시, 실행 반영은 다음 사이클"이다.

### 7.3 트레이딩 사이클 흐름

1. 인스턴스 스케줄 도래
2. 인스턴스 락 획득 실패 시 스킵 이벤트 기록
3. 미확정 주문 reconcile
4. 인스턴스 상태/비용 한도/auto-paused 여부 확인
5. 프롬프트와 전략 입력 스펙 기준의 판단 시점 설정값 확정
6. `held_only` 또는 `full_watchlist` 범위에 맞춰 시세/분봉/뉴스/공시/호가/매크로/이력/인사이트 입력 수집
7. LLM 판단 요청 및 원문 저장
8. 사이클 수준 판단 결과와 `orders[]` 출력 형식 검증
9. 유효한 판단이면 주문 실행안 구성, 실패면 사이클 판단 전체 실패 처리
10. 주문 실행 어댑터 호출
11. 주문 결과/자산/보유/판단 로그 저장
12. 메트릭 및 운영 이벤트 기록

## 8. 스케줄러와 트레이딩 사이클 분리

이 프로젝트의 핵심 분리 원칙은 "스케줄링"과 "사이클 실행"을 같은 책임으로 두지 않는 것이다.

- 스케줄러
  - 언제 인스턴스를 실행할지 결정
  - 활성/비활성 변화 반영
  - 중복 실행 방지용 락 확보 시도
- 사이클 실행기
  - 한 번 시작된 사이클의 모든 비즈니스 절차 수행
  - 판단 시점 설정값 고정
  - 판단, 주문, 저장, 로깅 처리

권장 구현:

- Spring `@Scheduled`는 "활성 인스턴스 스캔"까지만 담당
- 스케줄러 진입점은 `interfaces.scheduler`, 실제 사이클 실행 orchestration은 `trading.application.cycle`에 둔다.
- 실제 인스턴스 실행은 `TaskScheduler` 또는 전용 executor에 위임
- 인스턴스별 직렬성은 Redis 기반 분산 락으로 보장한다.
- 락 키는 `instance_id` 기준으로 구성하고 TTL은 2분으로 고정한다.
- 락은 사이클 시작 시 1회 획득하고, 워커 비정상 종료 시에는 TTL 만료로 정리한다.
- PostgreSQL advisory lock은 대안으로 검토할 수 있으나, 기본 권장안은 아니다.

이 구조가 필요한 이유:

- 긴 LLM 호출이나 외부 API 지연이 전체 스케줄러 쓰레드를 막지 않게 하기 위해
- 동일 인스턴스의 중복 실행과 다음 주기 skip 판정을 명확히 하기 위해
- 후속에 큐 기반 구조로 이동하더라도 실행기 코드를 재사용하기 위해

## 9. 영속성 경계

기본 저장소는 PostgreSQL을 권장한다. 이유는 트랜잭션, JSONB, 인덱싱, 운영 안정성 때문이다.

영속성 원칙:

- 전략 템플릿, 인스턴스, 프롬프트 버전, 모델 설정, 시스템 파라미터는 OLTP 테이블에 저장
- 판단 로그, 주문 이력, 자산 스냅샷, 보유 종목은 인스턴스 기준으로 강하게 분리
- 뉴스/공시/분봉/시장 스냅샷은 글로벌 데이터로 저장
- LLM 요청/응답 원문은 PostgreSQL의 대용량 테이블로 저장하고, 보존량이 큰 테이블에는 파티셔닝을 적용한다.
- audit log와 운영 이벤트 로그는 append-only

캐시/임시 저장 원칙:

- Redis 사용 권장
- 로그인 실패 카운트, IP 차단 저장
- 장중 호가/최근 시세/웹소켓 상태 같은 단기 캐시 저장
- 인스턴스 실행 락 저장

JPA 사용 시 주의:

- 쓰기 모델은 JPA 사용 가능
- 대시보드/차트/이력 화면의 복잡 조회는 Querydsl 또는 jOOQ 같은 조회 전용 접근을 허용
- 긴 트랜잭션 안에서 외부 API 호출 금지

## 10. 인증 및 보안 경계

운영자 인증은 웹 세션용 JWT로 한정하고, 워커 내부 권한은 별도 경계로 둔다.

- 운영자 로그인
  - 계정은 `app_user` 테이블에서 조회
  - 성공 시 JWT 쿠키 발급
  - sliding session 적용
- 인증 보호 대상
  - `/settings` 계열 편집 API
  - 운영용 내부 API
- 비로그인 허용 대상
  - 대시보드, 이력, 뉴스/공시, 차트

보안 원칙:

- JWT secret, 브로커 키, LLM 키는 GitHub Secrets로 관리하고 배포 시 서버의 `.env`로 주입
- reverse proxy 신뢰 체인을 전제로 client IP 추출 규칙을 명시적으로 구현
- 로그인 실패 5회/5분 차단은 Redis 기반으로 구현
- 상태 변경 요청은 CSRF 방어를 적용
- live 계좌 식별자는 저장/로그/UI에서 마스킹 규칙을 적용

워커 보안 원칙:

- 워커는 사용자 JWT를 사용하지 않는다.
- 브로커/LLM 호출 자격 증명은 서비스 계정 시크릿으로 분리한다.

## 11. 외부 연동 아키텍처

각 외부 시스템마다 공통 인터페이스와 벤더별 구현을 분리한다.
포트(interface)는 각 도메인 `application` 계층에 두고, 실제 벤더 구현체는 `integrations/*` 패키지에 둔다.

- `BrokerGateway`
  - `getAccountStatus`, `placeOrder`, `getOrderStatus`
- `MarketDataGateway`
  - `fetchSnapshot`, `fetchMinuteBars`, `subscribeTicks`
- `NewsGateway`
  - `fetchNews`
- `DisclosureGateway`
  - `fetchDisclosures`
- `InsightGateway`
  - `fetchSymbolInsights`
- `TradingDecisionEngine`
  - `requestTradingDecision`

공통 정책:

- 타임아웃, 재시도, circuit breaker를 어댑터 계층에 둔다.
- 원본 응답은 필요 범위만 저장하고 민감 정보는 마스킹한다.
- 벤더 장애는 도메인 예외로 표준화해 오래된 값(`stale`), 재시도 가능(`retryable`), 사이클 실패/경고 상태로 구분해 처리한다. 여기서 오래된 값(`stale`)은 최신 조회에는 실패했지만 마지막 성공값을 그대로 유지해 보여주는 상태를 뜻한다. 외부 API 장애만으로 인스턴스를 `auto_paused`로 전환하지는 않는다.

### 11.1 현재 spec 기준으로 확정된 연동

`spec2.md` 마지막 섹션 기준으로, 현재 설계가 전제해야 하는 외부 연동은 다음과 같다.

- 브로커/시장 데이터
  - 한국투자증권 KIS REST API
  - 한국투자증권 KIS WebSocket
- 정보 수집
  - 네이버 검색 뉴스 API
  - 기사 원문 직접 수집
  - DART 공시 API
  - Yahoo Finance (`yfinance`)
- LLM
  - `openclaw` / `nanobot` subprocess for 트레이딩 판단
  - 뉴스 유용성 판정도 LLM 기반으로 처리하며, 사용 모델은 글로벌 설정으로 관리한다.
- 운영 알림
  - Telegram Bot API
- 인바운드 운영 연동
  - GitHub webhook style의 `/api/deploy` 서명 요청

즉, 현재 범위의 LLM 연동은 트레이딩 판단용 subprocess 실행기를 감싸는 어댑터에 집중된다.

### 11.2 연동별 아키텍처 반영 원칙

- `BrokerGateway`
  - 초기 구현은 KIS 전용 어댑터를 만든다.
  - 계좌 조회, 주문 실행, 주문 상태 조회, 현재가/분봉 조회를 역할별로 분리한다.
- `MarketDataGateway`
  - KIS REST와 KIS WebSocket을 한 구현체 안에 섞지 말고, snapshot adapter와 streaming adapter를 분리한다.
- `NewsGateway`
  - 네이버 뉴스 검색 결과 수집과 기사 원문 fetch를 별도 단계로 둔다.
  - 원문 fetch 실패가 뉴스 row 자체 저장 실패로 전파되지 않게 분리한다.
- `DisclosureGateway`
  - DART 조회는 라이브러리 의존 코드를 어댑터 내부에만 가둔다.
- `TradingDecisionEngine`
  - 포트는 `trading.application`에 두고, `openclaw` / `nanobot` subprocess 구현은 `integrations.openclaw`, `integrations.nanobot`에 둔다.
  - Java에서는 `ProcessBuilder` 기반 subprocess 실행기로 구현한다.
  - 호출마다 `session_id`를 새로 생성해 CLI 인자로 전달한다.
  - 프롬프트는 CLI 메시지 인자로 전달하고, 응답 본문은 `stdout`, 오류 메시지는 `stderr`에서 수집한다.
  - timeout, stdout/stderr 수집, 종료코드 검사는 구현체가 담당한다.
  - 타임아웃 시에는 프로세스를 강제 종료하고 해당 판단을 `FAILED` 처리한다.
  - 종료코드가 0이 아니면 실행 실패로 처리하고, 인증 오류 키워드가 `stderr`에 포함된 경우에는 인증 오류로 별도 분기한다.
  - `stdout`이 비어 있거나 에러 키워드가 포함되면 유효하지 않은 응답으로 보고 `FAILED` 처리한다.
  - 같은 사이클 안에서 subprocess 재시도는 하지 않는다.
- `InsightGateway`
  - 전략이 opt-in한 경우에만 판단 대상 종목에 대한 종목별 인사이트/know-how 요약을 공급한다.
- `AlertGateway`
  - Telegram 전송을 담당한다.
- `DeployWebhook`
  - outbound integration이 아니라 inbound admin surface로 분류한다.
  - `WEBHOOK_SECRET` 검증은 auth/infrastructure 경계에서 수행한다.

## 12. 관측성

관측성은 기능이 아니라 운영 핵심이다. 최소 구성은 다음과 같다.

- 로그
  - JSON 구조 로그
  - `instanceId`, `cycleId`, `orderId`, `symbol`, `actor` 필드 공통화
- 메트릭
  - 사이클 시작/종료/스킵 수
  - 사이클 지연 시간
  - 외부 API 성공/실패/지연
  - LLM 토큰/비용
  - 로그인 실패 및 차단 수
- 트레이싱
  - 웹 요청, LLM 호출, 브로커 호출, 사이클 실행 구간 연결
- 헬스체크
  - DB, Redis, 브로커, 시장데이터, 뉴스, DART, LLM 의존성 상태 노출
  - KIS WebSocket 연결 상태와 마지막 수신 시각 노출
  - 수집 워커별 lag와 마지막 성공 시각 노출
  - 트레이딩 워커 마지막 사이클 시작/종료/스킵 시각 노출

대시보드의 시스템 상태 카드는 별도 하드코딩이 아니라, 메트릭/운영 이벤트 로그/수집기 상태를 읽어 계산하는 방식으로 구현한다.
`ops_event`는 구조 로그를 대체하는 주 저장소가 아니라, 운영 화면과 추적을 돕는 로그성 보조 저장소로 사용한다.

## 13. 구현 우선순위

1. `web-app` / `trading-worker` / `collector-worker` 분리 배포
2. PostgreSQL + Redis 기반 인스턴스 락/캐시
3. 전략/인스턴스/프롬프트/모델/파라미터 CRUD
4. 공개 조회 API와 운영자 인증
5. 최소 marketdata read path와 현재가/기본 분봉 입력 경로 확보
6. paper 모드 트레이딩 사이클 완성
7. live 브로커 어댑터와 reconcile
8. 뉴스/DART 수집과 분봉 backfill 확장

초기 릴리스에서는 `paper` 모드를 먼저 완성하되, 그 전에 최소한 현재가/분봉 기반의 `marketdata` 조회 경로가 준비되어야 한다. `live`는 동일한 사이클 구조에 주문 어댑터만 추가하는 방식이 바람직하다.

## 14. 확정 기술 스택

- 백엔드는 Java 21 + Spring Boot 3.x + Spring Web + Spring Security + Spring Data JPA를 사용한다.
- 관계형 DB는 PostgreSQL, 캐시는 Redis를 사용한다.
- 프론트엔드는 React + TypeScript + Vite를 사용한다.
- 실시간 UI는 WebSocket보다 polling 대시보드를 우선 적용한다.
