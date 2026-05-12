# 10. 공개 이력, 뉴스, 차트 API

목적:

- 나머지 공개 조회 화면에 필요한 read API를 닫는다.
- 페이징, 필터링, 상세 조회 계약을 여기서 고정한다.

선행조건:

- `09-public-dashboard-api.md`

참조 문서:

- `spec2.md`
- `docs/04-api-spec.md`

이번 작업 범위:

- 주문 이력 목록/상세 API
- 판단 로그 목록/상세 API
- 뉴스 목록/상세 API
- 공시 목록/상세 API
- 분봉/현재가 차트 API
- 최근 7일 기본 조회 규칙
- `page=1,size=20` 기본 페이징
- 전체 인스턴스 선택 시 `instanceName` 노출

이번 작업에서 제외:

- 실제 수집 워커
- 트레이딩 워커

필수 테스트 코드:

- `TradeHistoryApiTest`: 필터, 정렬, 페이징 규칙 검증
- `DecisionLogApiTest`: 판단 상세에 프롬프트/모델/토큰/주문 연결 정보가 포함되는지 검증
- `NewsApiTest`: 상태 필터와 기간 필터 검증
- `DisclosureApiTest`: 종목/기간 기준 조회 검증
- `ChartApiTest`: 분봉과 주문 시점 마커 응답 구조 검증
- `PublicReadAuthBoundaryTest`: 비로그인 접근 허용, 운영자 전용 API와의 경계 검증

완료 조건:

- `./gradlew test --tests "*TradeHistoryApiTest" --tests "*DecisionLogApiTest" --tests "*NewsApiTest" --tests "*DisclosureApiTest" --tests "*ChartApiTest" --tests "*PublicReadAuthBoundaryTest"`가 통과한다.
- 공개 조회 화면에서 필요한 read API 계약이 더 이상 빈칸 없이 고정된다.

