# 12. 뉴스, 공시, 매크로 수집 워커

목적:

- collector-worker의 나머지 수집 기능을 닫는다.
- 뉴스 목록/본문, 공시, 매크로, 뉴스 유용성 판단까지 연결한다.

선행조건:

- `11-collector-marketdata.md`

참조 문서:

- `spec2.md`
- `docs/01-system-architecture.md`
- `docs/07-external-integrations.md`

이번 작업 범위:

- 네이버 뉴스 목록 수집
- 기사 원문 fetch 분리
- 뉴스 유용성 LLM 판단
- DART 공시 수집
- 매크로 데이터 수집
- 수집 실패 `ops_event` 기록
- stale 상태 계산 입력 저장

이번 작업에서 제외:

- 트레이딩 판단용 LLM
- 주문 실행

필수 테스트 코드:

- `NaverNewsCollectorTest`: 뉴스 목록 메타와 종목 연결 저장 검증
- `ArticleBodyFetcherTest`: 본문 fetch 실패가 row 저장 실패로 번지지 않는지 검증
- `NewsAssessmentWorkflowTest`: 뉴스 유용성 판단 결과 저장 검증
- `DartDisclosureCollectorTest`: 공시 수집과 종목 매핑 검증
- `MacroCollectorTest`: 매크로 일일 스냅샷 저장 검증
- `ContentCollectorOpsEventTest`: 각 수집 실패가 `ops_event`에 기록되는지 검증

완료 조건:

- `./gradlew test --tests "*NaverNewsCollectorTest" --tests "*ArticleBodyFetcherTest" --tests "*NewsAssessmentWorkflowTest" --tests "*DartDisclosureCollectorTest" --tests "*MacroCollectorTest" --tests "*ContentCollectorOpsEventTest"`가 통과한다.
- 뉴스/공시/매크로 read API가 읽을 데이터가 주기적으로 채워지는 구조가 완성된다.

