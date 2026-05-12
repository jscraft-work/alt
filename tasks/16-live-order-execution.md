# 16. live 주문 실행과 reconcile

목적:

- `live` 인스턴스의 브로커 연동 경로를 닫는다.
- 주문 제출, 주문 상태 동기화, reconcile 실패 시 auto pause까지를 구현한다.

선행조건:

- `15-paper-order-execution.md`

참조 문서:

- `docs/05-trading-cycle-design.md`
- `docs/07-external-integrations.md`

이번 작업 범위:

- KIS 계좌 상태 조회 어댑터
- KIS 주문 제출 어댑터
- KIS 주문 상태 조회 어댑터
- `live` 주문 실행기
- `trade_order_intent -> trade_order(1:N)` 반영
- 부분체결/거절/실패 상태 반영
- reconcile 구현
- reconcile 실패 시 `auto_paused(reason=reconcile_failed)` 전환
- live 계좌 식별자 마스킹 유지

이번 작업에서 제외:

- 배포 자동화
- 운영 대시보드 추가 개선

필수 테스트 코드:

- `KisBrokerGatewayContractTest`: 계좌 조회/주문/상태 조회 응답 매핑 검증
- `LiveOrderExecutionTest`: live 주문 제출과 `trade_order` 생성 검증
- `LiveOrderPartialFillTest`: 부분체결 누적 상태 반영 검증
- `ReconcileWorkflowTest`: 미확정 주문을 브로커 상태로 정리하는지 검증
- `ReconcileFailureAutoPauseTest`: reconcile 실패 시 인스턴스 auto pause 검증

완료 조건:

- `./gradlew test --tests "*KisBrokerGatewayContractTest" --tests "*LiveOrderExecutionTest" --tests "*LiveOrderPartialFillTest" --tests "*ReconcileWorkflowTest" --tests "*ReconcileFailureAutoPauseTest"`가 통과한다.
- 추가 수동 완료 조건: sandbox 또는 실계좌 테스트 환경에서 최소 1회 주문 제출과 상태 조회를 검증하고 결과를 문서에 남긴다.

