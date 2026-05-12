# 07. 외부 연동 설계

## 1. 문서 목적과 범위

이 문서는 [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md), [01-system-architecture.md](/Users/jongsoobae/workspace/alt-java/docs/01-system-architecture.md), [05-trading-cycle-design.md](/Users/jongsoobae/workspace/alt-java/docs/05-trading-cycle-design.md), [06-auth-security.md](/Users/jongsoobae/workspace/alt-java/docs/06-auth-security.md)를 기준으로 외부 시스템 연동 경계와 실패 처리 규칙을 고정한다.

목표:

- 벤더별 연동 대상을 확정한다.
- 포트와 어댑터 경계를 고정한다.
- 인증값, 타임아웃, 재시도, 실패 시 제품 동작을 고정한다.
- 연동 테스트 기준을 제공한다.

비범위:

- 화면 API 상세
- DB 스키마 상세
- 브로커 주문 전략

## 2. 핵심 원칙

- 도메인 서비스는 벤더 SDK나 응답 형식에 직접 의존하지 않는다.
- 모든 outbound 연동은 `application` 포트와 `integrations` 어댑터로 분리한다.
- 벤더 실패는 그대로 전파하지 않고 공통 예외로 정리한다.
- 최신 조회 실패 후 마지막 성공값을 유지해 보여줄 수 있는 경우에는 오래된 값(`stale`) 상태로 처리한다.
- 외부 API 장애만으로 인스턴스를 `auto_paused`로 전환하지 않는다.
- 시크릿은 GitHub Secrets로 관리하고 배포 시 서버 `.env`로 주입한다.

## 3. 연동 대상 요약

| 연동 | 역할 | 방식 | 주요 설정값 |
|------|------|------|-------------|
| KIS REST API | 계좌 조회, 주문 실행, 주문 상태 조회, 현재가/분봉 조회 | HTTPS REST | `KIS_APP_KEY`, `KIS_APP_SECRET`, `KIS_HTS_ID`, `KIS_ACCT_STOCK` |
| KIS WebSocket | 실시간 체결/호가 수신 | WebSocket | `KIS_APP_KEY`, `KIS_APP_SECRET` |
| 네이버 검색 뉴스 API | 뉴스 목록 수집 | HTTPS REST | `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` |
| 기사 원문 직접 수집 | 기사 본문 수집 | HTTP GET | 없음 |
| DART 공시 API | 공시 목록 수집 | 라이브러리/HTTPS | `DART_API_KEY` |
| Yahoo Finance (`yfinance`) | 매크로 지표 수집 | 라이브러리/HTTPS | 없음 |
| `openclaw` | 기본 트레이딩 판단 LLM 실행 | subprocess | 실행 파일 경로, 모델 설정 |
| `nanobot` | 고성능 트레이딩 판단 LLM 실행 | subprocess | 실행 파일 경로, 모델 설정 |
| Telegram Bot API | 운영 알림 전송 | HTTPS REST | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` |
| GitHub webhook style `/api/deploy` | 배포 요청 수신 | inbound HTTP | `WEBHOOK_SECRET` |

## 4. 포트와 어댑터 구조

포트는 도메인 `application` 계층에 두고, 구현체는 `integrations/*` 아래에 둔다.

- `BrokerGateway`
  - `getAccountStatus`
  - `placeOrder`
  - `getOrderStatus`
- `MarketDataGateway`
  - `fetchPrice`
  - `fetchMinuteBars`
  - `fetchOrderBook`
  - `subscribeTicks`
- `NewsGateway`
  - `fetchNews`
  - `fetchArticleBody`
- `DisclosureGateway`
  - `fetchDisclosures`
- `MacroGateway`
  - `fetchDailyMacro`
- `TradingDecisionEngine`
  - `requestTradingDecision`
- `AlertGateway`
  - `sendMessage`

구현 패키지 예시:

- `integrations.kis.rest`
- `integrations.kis.ws`
- `integrations.naver`
- `integrations.dart`
- `integrations.yahoo`
- `integrations.openclaw`
- `integrations.nanobot`
- `integrations.telegram`

비고:

- `/api/deploy`는 outbound 어댑터가 아니라 inbound 운영 엔드포인트다.
- `WEBHOOK_SECRET` 검증은 인증/보안 경계에서 처리한다.

## 5. 공통 연동 정책

### 5.1 타임아웃과 재시도

- 타임아웃은 어댑터 계층에서 적용한다.
- 같은 요청을 무조건 재시도하지 않는다.
- 주문 실행, LLM 판단 호출처럼 중복 실행 위험이 큰 연동은 같은 사이클 안에서 재시도하지 않는다.
- 뉴스/공시/매크로 같은 수집 계열은 안전한 범위에서 제한적 재시도를 허용할 수 있다.

### 5.2 공통 실패 분류

어댑터는 벤더별 오류를 아래 의미로 정리해 상위 계층에 전달한다.

- 인증 실패
- 요청 형식 오류
- 일시 장애
- 타임아웃
- 응답 형식 오류
- 빈 응답

상위 계층 처리 원칙:

- 시장 데이터 조회 실패: 시스템 상태 카드 경고, 필요 시 해당 사이클 `FAILED`
- 뉴스/공시/매크로 수집 실패: 수집 실패로 기록하고 다음 주기 재시도
- LLM 호출 실패: 판단 로그 `FAILED`
- Telegram 전송 실패: 구조 로그와 `ops_event`에 남기고 사용자 요청 흐름은 막지 않음

### 5.3 관측성

모든 어댑터는 아래를 남긴다.

- 요청 시작/종료 시각
- 대상 벤더
- 성공/실패 결과
- 타임아웃 여부
- 마스킹된 식별자

민감 값은 로그에 남기지 않는다.

## 6. 브로커 연동

### 6.1 대상

초기 구현은 한국투자증권 KIS 전용이다.

구분:

- REST: 계좌, 주문, 주문 상태, 현재가, 분봉
- WebSocket: 실시간 체결, 호가

### 6.2 REST 역할

- `getAccountStatus`
  - 현금, 총자산, 보유 포지션 조회
- `placeOrder`
  - `paper`가 아닌 `live` 주문 제출
- `getOrderStatus`
  - `live` reconcile에 사용
- `fetchPrice`
  - 현재가/시장 스냅샷 조회
- `fetchMinuteBars`
  - 차트와 판단 입력용 1분봉 조회

### 6.3 WebSocket 역할

- 실시간 틱/체결 수신
- 실시간 호가 수신
- 시스템 상태 카드의 연결 상태 표시

호가 원본은 PostgreSQL에 저장하지 않고 Redis TTL 2일 데이터로만 유지한다.

### 6.4 실패 처리

- 현재가/분봉 조회 실패:
  - 마지막 성공값이 있으면 `stale`
  - 필수 입력 구성에 실패하면 해당 사이클 `FAILED`
- 주문 실행 실패:
  - `trade_order_intent`는 남기고 `trade_order` 상태는 실패로 기록
- 주문 상태 조회 실패:
  - `live` reconcile 단계 실패로 간주
  - 인스턴스를 `auto_paused(reason=reconcile_failed)`로 전환

## 7. 뉴스 연동

### 7.1 네이버 검색 뉴스 API

역할:

- 종목 키워드 기준 뉴스 목록 수집

저장 원칙:

- 목록 메타는 `news_item`
- 종목 연결은 `news_asset_relation`

### 7.2 기사 원문 직접 수집

역할:

- 네이버 검색 결과의 `link` 대상 페이지에서 본문 확보
- 요약 및 유용성 판단 입력 구성

원칙:

- 원문 fetch 실패가 뉴스 row 저장 실패로 전파되면 안 된다.
- 목록 수집과 본문 수집은 별도 단계로 둔다.

### 7.3 실패 처리

- 네이버 API 실패: 수집 실패로 기록 후 다음 주기 재시도
- 기사 원문 fetch 실패: 뉴스 row는 유지하고 본문/요약만 비움

## 8. 공시 연동

### 8.1 DART

역할:

- 최근 공시 수집
- 종목의 `dart_corp_code` 기준 연결

원칙:

- DART 라이브러리 의존은 어댑터 내부에만 둔다.
- `disclosure_item`은 공시 자체를 저장하고, 종목 연결은 `dart_corp_code`로 해석한다.

### 8.2 실패 처리

- 공시 수집 실패는 수집 실패로만 기록한다.
- 시장 데이터나 트레이딩 인스턴스 상태를 직접 바꾸지 않는다.

## 9. 매크로 연동

역할:

- 환율, 해외지수, 금리 등 일일 매크로 지표 수집

원칙:

- `macro_item`은 일일 단위 글로벌 데이터다.
- 수집 실패 시 직전 성공값을 유지할 수 있으나, 필요 시 `stale`로 표시한다.

## 10. LLM subprocess 연동

### 10.1 대상

- `openclaw`
- `nanobot`

### 10.2 실행 규칙

- Java `ProcessBuilder` 기반으로 실행한다.
- 호출마다 새 `session_id`를 생성한다.
- 프롬프트는 CLI 인자로 전달한다.
- 응답 본문은 `stdout`, 오류는 `stderr`에서 읽는다.
- timeout 시 프로세스를 강제 종료한다.
- 같은 사이클 안에서 재시도하지 않는다.

### 10.3 결과 처리

- 종료코드 `0`이 아니면 실패
- `stderr` 인증 오류 키워드는 인증 실패로 분리
- `stdout`이 비었으면 실패
- `stdout`에 에러 키워드가 있으면 실패

실패 시:

- `trade_decision_log`는 `FAILED`
- `trade_cycle_log`도 `FAILED`

## 11. Telegram 연동

역할:

- 운영 알림 전송

전송 대상 예시:

- 트레이딩 사이클 실패
- reconcile 실패
- 외부 API 장애
- 비용 상한 초과 알림
- 배포 알림

실패 처리:

- Telegram 전송 실패가 원래 업무 흐름을 되돌리지는 않는다.
- 구조 로그와 `ops_event`에 전송 실패를 남긴다.

## 12. 배포 연동

`/api/deploy`는 외부로 호출하는 연동이 아니라 외부에서 들어오는 운영 요청이다.

원칙:

- `WEBHOOK_SECRET` 서명 검증 필수
- 운영자 인증 경계와 분리된 별도 검증 처리
- 요청 본문과 헤더는 감사 로그/구조 로그에 남기되 민감 값은 마스킹

## 13. 구현 체크리스트

- KIS REST 어댑터와 WebSocket 어댑터 분리
- 뉴스 목록 수집과 기사 원문 fetch 분리
- DART 의존 코드는 어댑터 내부에만 배치
- `yfinance`는 매크로 전용으로 한정
- `openclaw` / `nanobot`는 `ProcessBuilder` 기반 subprocess로 실행
- LLM 호출은 같은 사이클에서 재시도하지 않음
- Telegram 실패가 본업 흐름을 막지 않음
- 모든 시크릿은 GitHub Secrets에서 서버 `.env`로 주입
