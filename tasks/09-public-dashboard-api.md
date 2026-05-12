# 09. 공개 대시보드 API

목적:

- 비로그인 사용자가 보는 핵심 운영 화면의 조회 API를 먼저 닫는다.
- 전략 오버뷰, 인스턴스 대시보드, 시스템 상태 계산을 이 task에서 끝낸다.

선행조건:

- `08-admin-prompt-and-watchlist.md`

참조 문서:

- `spec2.md`
- `docs/04-api-spec.md`
- `docs/05-trading-cycle-design.md`

이번 작업 범위:

- `GET /api/dashboard/strategy-overview`
- `GET /api/dashboard/instances/{strategyInstanceId}`
- 시스템 상태 카드 계산
- 장중/장외 상태 표현
- stale 데이터 표시 기준
- KST 기준 시각 응답
- 전체 선택 시 전략 오버뷰 fallback 규칙 반영

이번 작업에서 제외:

- 주문/판단 로그 상세 조회
- 뉴스/공시/차트 조회

필수 테스트 코드:

- `StrategyOverviewApiTest`: 비로그인으로 카드 목록 조회 가능 여부 검증
- `InstanceDashboardApiTest`: 자산/보유/최근 활동 응답 구조 검증
- `SystemStatusClassifierTest`: 정상/지연/중단/장외 판정 규칙 검증
- `DashboardStaleDataTest`: 외부 데이터 stale 시 마지막 성공값 유지와 신뢰도 배지 계산 검증
- `DashboardTimeZoneSerializationTest`: 응답 시간이 KST로 직렬화되는지 검증

완료 조건:

- `./gradlew test --tests "*StrategyOverviewApiTest" --tests "*InstanceDashboardApiTest" --tests "*SystemStatusClassifierTest" --tests "*DashboardStaleDataTest" --tests "*DashboardTimeZoneSerializationTest"`가 통과한다.
- 로그인 없이 대시보드 조회가 가능하고, 보안 테스트에서 차단되지 않는다.

