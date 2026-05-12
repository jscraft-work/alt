# 14. 트레이딩 판단 파이프라인

목적:

- 한 번의 사이클 안에서 입력 조립, LLM 호출, 응답 검증, 판단 로그 저장까지를 닫는다.
- 주문 실행 전 단계의 실패 처리 규칙을 여기서 고정한다.

선행조건:

- `13-trading-cycle-scheduler.md`

참조 문서:

- `docs/05-trading-cycle-design.md`
- `docs/07-external-integrations.md`

이번 작업 범위:

- 입력 데이터 조립기 구현
- `ProcessBuilder` 기반 LLM subprocess 어댑터
- timeout, non-zero exit, 빈 응답, 파싱 실패 분류
- 판단 결과 구조 검증
- `trade_decision_log` 저장
- `trade_order_intent` 생성
- 최소 안전검증
- 실패 시 `ops_event`와 운영 알림 발행

이번 작업에서 제외:

- `paper/live` 실제 주문 제출
- 포트폴리오 갱신

필수 테스트 코드:

- `TradingInputAssemblerTest`: 입력 스펙에 맞는 데이터 조회 범위가 조립되는지 검증
- `LlmSubprocessAdapterTest`: 정상 종료, timeout, non-zero exit, stderr 오류를 분류하는지 검증
- `DecisionParserValidationTest`: 허용 상태값/필수 필드/내용 충돌 검증
- `TradeDecisionLogPersistenceTest`: 성공/실패 판단 로그 저장 검증
- `TradeOrderIntentGenerationTest`: `orders[]`가 intent로 변환되는지 검증
- `OrderIntentSafetyValidationTest`: 수량/가격/보유/현금 검증 실패 시 execution blocked 처리 검증

완료 조건:

- `./gradlew test --tests "*TradingInputAssemblerTest" --tests "*LlmSubprocessAdapterTest" --tests "*DecisionParserValidationTest" --tests "*TradeDecisionLogPersistenceTest" --tests "*TradeOrderIntentGenerationTest" --tests "*OrderIntentSafetyValidationTest"`가 통과한다.
- LLM 실패 시 `trade_decision_log=FAILED`, `trade_cycle_log=FAILED`가 테스트로 재현된다.

