# 구현 순서 및 체크리스트

## 1. 목적

이 문서는 [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md)와 [docs/01-system-architecture.md](/Users/jongsoobae/workspace/alt-java/docs/01-system-architecture.md)~[docs/07-external-integrations.md](/Users/jongsoobae/workspace/alt-java/docs/07-external-integrations.md)를 실제 개발 순서로 풀어쓴 실행 체크리스트다.

목표:

- 어떤 순서로 구현해야 하는지 고정
- 각 단계의 완료 기준을 고정
- 이 문서만 따라가도 v1 개발을 끝낼 수 있게 정리

## 2. 사용 방법

- 각 단계는 앞 단계를 통과한 뒤 진행한다.
- 체크박스는 코드, 테스트, 문서 반영까지 끝났을 때만 완료 처리한다.
- 구현 중 정책 변경이 생기면 먼저 관련 기준 문서를 수정한 뒤 체크리스트를 진행한다.

## 3. 전체 단계 요약

1. 프로젝트 부트스트랩
2. DB/마이그레이션 기반 모델링
3. 인증/보안
4. 운영자 설정 도메인과 CRUD
5. 공개 조회 API
6. 수집 워커
7. 트레이딩 사이클 코어
8. `paper` 주문 실행
9. `live` 주문 실행
10. 운영/배포/테스트 마감

## 4. Phase 1. 프로젝트 부트스트랩

참조:

- [docs/01-system-architecture.md](/Users/jongsoobae/workspace/alt-java/docs/01-system-architecture.md)
- [docs/06-auth-security.md](/Users/jongsoobae/workspace/alt-java/docs/06-auth-security.md)

체크리스트:

- [x] Java 21 + Spring Boot 3.x 기준 프로젝트 생성
- [x] 멀티 모듈 또는 패키지 구조 골격 생성
- [x] `web-app`, `trading-worker`, `collector-worker` 실행 구분 방식 결정 및 반영
- [x] PostgreSQL, Redis 연결 설정 추가
- [x] 환경변수 로딩 방식 확정
- [x] `.env.example` 작성
- [ ] GitHub Secrets -> 서버 `.env` 주입 기준을 README 또는 배포 스크립트에 반영
- [x] Actuator health endpoint 추가
- [x] 공통 예외 응답 포맷 추가
- [x] 공통 로깅/JSON 로그 포맷 추가

완료 기준:

- [x] 로컬에서 앱이 뜬다
- [x] PostgreSQL/Redis 연결 확인이 된다
- [x] health endpoint가 정상 응답한다

## 5. Phase 2. DB/마이그레이션 기반 모델링

참조:

- [docs/02-domain-model.md](/Users/jongsoobae/workspace/alt-java/docs/02-domain-model.md)
- [docs/03-database-schema.md](/Users/jongsoobae/workspace/alt-java/docs/03-database-schema.md)

체크리스트:

- [x] Flyway 설정 추가
- [x] `V1__init.sql` 작성
- [x] 핵심 운영 테이블 생성
  - [x] `app_user`
  - [x] `broker_account`
  - [x] `strategy_template`
  - [x] `strategy_instance`
  - [x] `strategy_instance_prompt_version`
  - [x] `strategy_instance_watchlist_relation`
  - [x] `trade_cycle_log`
  - [x] `trade_decision_log`
  - [x] `trade_order_intent`
  - [x] `trade_order`
  - [x] `portfolio`
  - [x] `portfolio_position`
  - [x] `audit_log`
  - [x] `ops_event`
- [x] 수집 테이블 생성
  - [x] `market_price_item`
  - [x] `market_minute_item`
  - [x] `news_item`
  - [x] `news_asset_relation`
  - [x] `disclosure_item`
  - [x] `macro_item`
- [x] `market_intraday_quote_item`은 DB 테이블이 아니라 Redis 키 구조로 설계
- [x] 파티셔닝 대상 테이블 반영
- [x] enum 대신 `varchar + check constraint` 기준 반영
- [x] JPA 엔티티/리포지토리 초안 작성
- [x] optimistic lock용 `version` 컬럼 반영

완료 기준:

- [x] 빈 DB에서 Flyway가 끝까지 적용된다
- [x] 엔티티 매핑 부팅 오류가 없다
- [x] 최소 persistence 테스트가 통과한다

## 6. Phase 3. 인증/보안

참조:

- [docs/04-api-spec.md](/Users/jongsoobae/workspace/alt-java/docs/04-api-spec.md)
- [docs/06-auth-security.md](/Users/jongsoobae/workspace/alt-java/docs/06-auth-security.md)

체크리스트:

- [x] `app_user` 기반 운영자 인증 구현
- [x] 비밀번호 해시 `Argon2id` 적용
- [x] JWT 쿠키 발급 구현
- [x] `HttpOnly + Secure + SameSite=Lax` 적용
- [x] sliding session 구현
  - [x] idle timeout 8시간
  - [x] absolute max session 7일
  - [x] 재발급 기준 2시간 이하
- [x] CSRF double-submit cookie 구현
  - [x] `XSRF-TOKEN`
  - [x] `X-CSRF-TOKEN`
- [x] `/api/auth/login`
- [x] `/api/auth/logout`
- [x] `/api/auth/me`
- [x] `/api/auth/csrf`
- [x] 로그인 실패 5회/5분 차단 Redis 구현
- [x] reverse proxy 신뢰 체인 기반 client IP 판정 구현
- [x] 인증/로그아웃/차단 감사 로그 기록 구현

완료 기준:

- [x] 로그인/로그아웃이 MockMvc 통합 테스트로 재현된다 (브라우저 수동 확인 대체)
- [x] 보호 API가 쿠키 기반으로 정상 보호된다
- [x] CSRF 없는 상태 변경 요청이 차단된다
- [x] 로그인 실패 차단이 동작한다

## 7. Phase 4. 운영자 설정 도메인과 CRUD

참조:

- [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md)
- [docs/02-domain-model.md](/Users/jongsoobae/workspace/alt-java/docs/02-domain-model.md)
- [docs/04-api-spec.md](/Users/jongsoobae/workspace/alt-java/docs/04-api-spec.md)

체크리스트:

- [x] 전략 템플릿 CRUD
- [x] 전략 인스턴스 CRUD
- [x] 프롬프트 편집 및 인스턴스 프롬프트 버전 이력 구현
- [x] 감시 종목 CRUD
- [x] 종목 마스터 CRUD
- [x] `dart_corp_code` 자동 lookup + 수동 입력 UX에 맞는 API 구현
- [x] 모델 프로필 CRUD
- [x] 전역 운영 파라미터 CRUD
- [x] `draft / active / inactive` lifecycle 전이 구현
- [x] 활성화 전 검증 구현
  - [x] 프롬프트 존재
  - [x] 모델 존재
  - [x] 프롬프트 frontmatter 파싱 성공 (구 "입력 스펙 존재" 검증을 대체)
  - [x] 실행 주기 존재
  - [x] 전략 예산 존재
  - [x] `live`면 연결 계좌 존재
- [x] 설정 수정 API optimistic lock 구현
- [x] 감사 로그 기록 구현

완료 기준:

- [x] 템플릿 생성 -> 인스턴스 생성 -> 활성화가 된다
- [x] 오래된 `version`으로 저장 시 `409`가 난다
- [x] 프롬프트 버전 복원 기능이 동작한다

## 8. Phase 5. 공개 조회 API

참조:

- [docs/04-api-spec.md](/Users/jongsoobae/workspace/alt-java/docs/04-api-spec.md)

체크리스트:

- [x] 대시보드 전략 오버뷰 API
- [x] 인스턴스 대시보드 API
- [x] 시스템 상태 카드 API
- [x] 판단 로그 목록/상세 API
- [x] 주문 이력 목록/상세 API
- [x] 뉴스 목록/상세 API
- [x] 공시 목록/상세 API
- [x] 차트용 분봉/현재가 API
- [x] 공개 조회 API는 비로그인 허용
- [x] KST 고정 시간 표시 기준 반영

완료 기준:

- [x] 로그인 없이 대시보드, 이력, 뉴스/공시, 차트 조회가 된다
- [x] `trade_cycle_log`, `trade_decision_log`, `trade_order`가 화면 조회에 연결된다

## 9. Phase 6. 수집 워커

참조:

- [docs/01-system-architecture.md](/Users/jongsoobae/workspace/alt-java/docs/01-system-architecture.md)
- [docs/07-external-integrations.md](/Users/jongsoobae/workspace/alt-java/docs/07-external-integrations.md)

체크리스트:

- [x] collector-worker 실행 엔트리포인트 구현
- [x] KIS 현재가 수집
- [x] KIS 분봉 수집
- [x] KIS WebSocket 연결 및 상태 추적
- [x] 장중 호가 원본 Redis TTL 2일 저장
- [x] 네이버 뉴스 목록 수집
- [x] 기사 원문 fetch 분리 구현
- [x] 뉴스 유용성 LLM 판단 구현
- [x] DART 공시 수집
- [x] 매크로 수집
- [x] 수집 실패 시 `ops_event` 기록
- [x] 수집 지연 상태 계산 구현

완료 기준:

- [x] `market_price_item`, `market_minute_item`, `news_item`, `disclosure_item`, `macro_item`이 주기적으로 채워진다
- [x] 장중 호가가 Redis에 들어간다
- [x] 수집 실패/지연이 상태 카드에 반영된다

## 10. Phase 7. 트레이딩 사이클 코어

참조:

- [docs/05-trading-cycle-design.md](/Users/jongsoobae/workspace/alt-java/docs/05-trading-cycle-design.md)
- [docs/07-external-integrations.md](/Users/jongsoobae/workspace/alt-java/docs/07-external-integrations.md)

체크리스트:

- [x] trading-worker 실행 엔트리포인트 구현
- [x] 활성 인스턴스 스케줄링 구현 — db-scheduler `recurringWithPersistentSchedule` + `TradingCycleReconciler` (state-based reconciler가 1분 주기로 `strategy_instance` ↔ `scheduled_tasks` 동기화)
- [x] 인스턴스별 직렬 실행 보장 — `scheduled_tasks` `(task_name, task_instance)` PK + `picked` 플래그. 외부 Redis 락 미사용 (구 `TradingInstanceLock` 제거)
- [x] `trade_cycle_log` 생성 및 단계 갱신 구현
- [x] 판단 시점 설정값 고정 구현
- [x] 프롬프트 frontmatter 기반 입력 데이터 조립기 구현 (`PromptInputSpecParser` → `PromptContextAssembler`; 구 `inputSpec` JSON 기반 조립기 폐기)
- [x] 프롬프트 frontmatter + Pebble template engine 도입 (`PromptTemplateEngine`, Pebble = Jinja2 호환 Java 템플릿)
- [x] 프롬프트 변수 카탈로그 API (`GET /api/admin/prompt-vocabulary`)
- [x] LLM HTTP 호출기 구현 (`LlmHttpAdapter` — 호스트 launchd wrapper `POST /ask`)
- [x] LLM 응답 파싱 및 내용 검증 구현
- [x] `trade_decision_log` 기록 구현
- [x] `trade_order_intent` 생성 구현
- [x] 최소 안전검증 구현
- [x] `ops_event` 기록 구현 (trading 측: `TradingOpsEventRecorder` — llm_failed, decision_parse_failed, reconcile_failed, auto_paused, live_order_failed)
- [x] Telegram 운영 알림 연동 구현 (`TradingIncidentReporter`가 LLM/decision/reconcile/auto_paused/live order 실패 시 `AlertGateway.dispatch` 호출)
- [ ] 비용 추정 합산 및 상한 초과 알림 구현 — v1 범위에서 보류 (LLM 호출이 무료라 우선순위 낮음)

완료 기준:

- [x] 활성 인스턴스가 주기마다 사이클을 돈다
- [x] `trade_cycle_log`와 `trade_decision_log`가 정상 기록된다
- [x] LLM 실패 시 `FAILED`가 기록된다

## 11. Phase 8. `paper` 주문 실행

참조:

- [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md)
- [docs/05-trading-cycle-design.md](/Users/jongsoobae/workspace/alt-java/docs/05-trading-cycle-design.md)

체크리스트:

- [x] `paper` 주문 실행기 구현
- [x] `MARKET` 주문 체결가를 최신 현재가 스냅샷으로 결정
- [x] `LIMIT` 주문 즉시 체결 처리
- [x] 주문별 `trade_order` 생성
- [x] 주문 완료 후 `portfolio` 갱신
- [x] 주문 완료 후 `portfolio_position` 갱신
- [x] `trade_order.portfolio_after_json` 기록
- [x] 판단 로그와 주문 이력 연결

완료 기준:

- [x] `paper` 인스턴스가 end-to-end로 돈다
- [x] 판단 -> 주문안 -> 주문 -> 포트폴리오 갱신이 이어진다
- [x] 대시보드 현재 자산과 포지션이 정상 반영된다

## 12. Phase 9. `live` 주문 실행

참조:

- [docs/05-trading-cycle-design.md](/Users/jongsoobae/workspace/alt-java/docs/05-trading-cycle-design.md)
- [docs/07-external-integrations.md](/Users/jongsoobae/workspace/alt-java/docs/07-external-integrations.md)

체크리스트:

- [x] `broker_account` 관리 구현
- [x] `live` 인스턴스 계좌 연결 구현
- [x] KIS 계좌 상태 조회 연동
- [x] KIS 주문 제출 연동
- [x] KIS 주문 상태 조회 연동
- [x] reconcile 구현
- [x] reconcile 실패 시 `auto_paused(reason=reconcile_failed)` 구현
- [x] `trade_order_intent -> trade_order(1:N 가능)` 반영
- [x] live 실패/거절/부분체결 상태 반영
- [x] live 계좌 식별자 마스킹 반영

완료 기준:

- [x] `live` 주문 제출 경로가 어댑터 계약 테스트로 닫힘 (실 브로커 sandbox 1회 검증은 별도 수동 완료 조건)
- [x] reconcile 실패 시 자동 일시중지가 동작한다
- [x] 주문 상태 조회 결과가 DB에 반영된다

## 13. Phase 10. 운영/배포/테스트 마감

참조:

- [docs/01-system-architecture.md](/Users/jongsoobae/workspace/alt-java/docs/01-system-architecture.md)
- [docs/06-auth-security.md](/Users/jongsoobae/workspace/alt-java/docs/06-auth-security.md)
- [docs/07-external-integrations.md](/Users/jongsoobae/workspace/alt-java/docs/07-external-integrations.md)

체크리스트:

- [x] 구조 로그 정리
- [x] `ops_event` 저장 정책 정리
- [x] Telegram 알림 on/off, 음소거 시간대 구현
- [x] health/readiness 점검 보강
- [ ] reverse proxy 배포 설정 문서화
- [ ] GitHub Actions 배포 흐름 정리
- [ ] GitHub Secrets -> 서버 `.env` 주입 자동화
- [x] 로컬/운영 환경별 프로파일 정리
- [x] 기본 seed 데이터 작성
- [x] 운영자 최초 계정 생성 절차 정리 (seed 코드 기준 — 실서버 검증은 별도 수동 완료 조건)

테스트 체크리스트:

- [x] 단위 테스트
- [x] 리포지토리 테스트
- [x] 인증/보안 통합 테스트
- [x] 설정 CRUD API 테스트
- [x] 수집 어댑터 계약 테스트
- [x] LLM subprocess 어댑터 테스트
- [x] `paper` 사이클 end-to-end 테스트
- [x] `live` 주문 연동 테스트 (어댑터 계약 + reconcile 회귀, 실 sandbox는 수동 완료 조건)
- [x] 실패/타임아웃/재시도 금지 정책 테스트

완료 기준:

- [x] 로컬 개발 환경에서 전체 기능이 재현된다
- [x] `paper`는 완전 동작한다
- [ ] `live`는 최소 1회 실제 연동 검증 또는 sandbox 검증을 통과한다
- [x] 인증, 수집, 사이클, 주문, 알림의 주요 실패 경로 테스트가 있다

## 14. 최종 완료 판정

아래를 모두 만족하면 v1 개발 완료로 본다.

- [x] 운영자 로그인/로그아웃과 보안 정책이 동작한다
- [x] 전략 템플릿/인스턴스 설정이 가능하다
- [x] 공개 조회 화면용 API가 모두 동작한다
- [x] 수집 워커가 시장/뉴스/공시/매크로 데이터를 채운다
- [x] `paper` 트레이딩 사이클이 end-to-end로 동작한다
- [ ] `live` 주문 연동이 최소 검증 범위를 통과한다 (KIS sandbox 또는 실계좌 1회 검증 필요)
- [x] 운영 알림과 감사 로그가 남는다
- [x] 주요 테스트가 통과한다 (CI 파이프라인 자체 구축은 별도 항목)

## 15. 남은 항목 요약

코드/테스트는 v1 범위에서 닫혔고, 다음만 남았다.

### 코드 영역 (보류)
- [ ] 비용 추정 합산 및 일일 상한 초과 알림 — 무료 LLM 사용 중이라 v1 보류

### 운영/배포 산출물
- [x] reverse proxy 배포 설정 문서화 — `jscraft-infra/docs/deployment.md` + `alt-java/DEPLOY.md`로 갈음
- [x] GitHub Actions 배포 흐름 정리 — `alt-java/.github/workflows/ci.yml` (Deploy workflow) 완료
- [x] GitHub Secrets → 서버 `.env` 주입 자동화 — deploy 서버 `/webhook/env-sync` (jscraft-infra/deploy/index.js) 완료
- [x] postgres init.sql에 `alt` DB 멱등 생성 추가 — 운영 서버는 PGDATA가 이미 차 있어서 자동 실행은 안 되며, 운영자가 한 번 `CREATE DATABASE alt` 수동 실행 필요

### 수동 검증 게이트 (사람 손)
- [ ] db-scheduler 운영자 시나리오 검증 — active 토글 → `scheduled_tasks` row 생성/cancel, 워커 kill 후 재기동 시 사이클 이어짐 확인
- [ ] KIS sandbox 또는 실계좌에서 live 주문 1회 제출/상태 조회 검증
- [ ] 실 서버에 배포 후 health/사이클 1회 통과 확인
- [ ] Telegram 실제 chat ID로 LLM/reconcile/auto_paused 알림 발송 — 본문/마스킹/음소거 동작 사람 눈 확인
- [ ] LLM HTTP wrapper(맥미니 launchd `127.0.0.1:18000`) 실 호출 1회 — `host.docker.internal:18000`이 컨테이너에서 도달하는지 확인
- [ ] 운영자가 admin UI에서 prompt frontmatter + Pebble template으로 새 인스턴스 활성화 → 사이클 1회 LLM 호출 + `trade_decision_log` 정상 기록 확인
