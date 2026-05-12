# 11. 시장 데이터 수집 워커

목적:

- collector-worker의 첫 번째 축인 시장 데이터 수집을 구현한다.
- 가격, 분봉, 웹소켓 상태, 장중 호가 Redis 캐시를 먼저 닫는다.

선행조건:

- `10-public-history-news-chart-api.md`

참조 문서:

- `docs/01-system-architecture.md`
- `docs/05-trading-cycle-design.md`
- `docs/07-external-integrations.md`

이번 작업 범위:

- `collector-worker` 실행 진입점
- KIS 현재가 수집
- KIS 분봉 수집
- KIS WebSocket 연결 상태 추적
- 장중 호가 원본 Redis TTL 2일 저장
- 수집 실패 시 `ops_event` 기록
- 시스템 상태 카드 입력값 저장

이번 작업에서 제외:

- 뉴스 수집
- 공시 수집
- 매크로 수집

필수 테스트 코드:

- `CollectorWorkerBootstrapTest`: collector profile에서 필요한 빈만 뜨는지 검증
- `KisPriceCollectorTest`: 현재가 수집 결과가 `market_price_item`에 저장되는지 검증
- `KisMinuteBarCollectorTest`: 분봉 수집과 upsert/중복 방지 규칙 검증
- `OrderBookRedisCacheTest`: 호가 원본이 TTL 2일로 Redis에 저장되는지 검증
- `MarketDataOpsEventTest`: 외부 연동 실패 시 `ops_event`가 남는지 검증
- `WebSocketStatusTrackerTest`: 연결/지연/끊김 상태 계산 입력이 갱신되는지 검증

완료 조건:

- `./gradlew test --tests "*CollectorWorkerBootstrapTest" --tests "*KisPriceCollectorTest" --tests "*KisMinuteBarCollectorTest" --tests "*OrderBookRedisCacheTest" --tests "*MarketDataOpsEventTest" --tests "*WebSocketStatusTrackerTest"`가 통과한다.
- 테스트 더블 또는 WireMock 기반으로 시장 데이터 수집 흐름이 재현된다.

