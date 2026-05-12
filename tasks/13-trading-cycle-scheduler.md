# 13. 트레이딩 사이클 스케줄러

목적:

- `trading-worker`의 상위 orchestration을 먼저 닫는다.
- 활성 인스턴스 선택, 직렬 실행 보장, 사이클 로그 생성까지를 이 task에서 끝낸다.

선행조건:

- `12-collector-news-disclosure-macro.md`

참조 문서:

- `docs/01-system-architecture.md`
- `docs/05-trading-cycle-design.md`

이번 작업 범위:

- `trading-worker` 실행 진입점
- 활성 인스턴스 스케줄 등록
- 실행 시간대 판정
- Redis 락 획득과 스킵 처리
- `trade_cycle_log` 생성과 단계 전이
- 사이클 시작 시 설정 스냅샷 고정
- 입력 데이터 조립 진입점 골격

이번 작업에서 제외:

- LLM 호출
- 주문 실행
- reconcile 상세

필수 테스트 코드:

- `TradingWorkerBootstrapTest`: trading profile 기동 검증
- `ActiveInstanceSchedulerTest`: 활성 인스턴스만 스케줄 대상이 되는지 검증
- `TradingWindowPolicyTest`: KST 기준 실행 시간대 판정 검증
- `RedisLockSerialExecutionTest`: 같은 인스턴스 중복 실행이 막히는지 검증
- `TradeCycleLogLifecycleTest`: `READY`부터 다음 단계 전이가 기록되는지 검증
- `SettingsSnapshotFreezeTest`: 사이클 시작 후 설정 변경이 현재 사이클에 반영되지 않는지 검증

완료 조건:

- `./gradlew test --tests "*TradingWorkerBootstrapTest" --tests "*ActiveInstanceSchedulerTest" --tests "*TradingWindowPolicyTest" --tests "*RedisLockSerialExecutionTest" --tests "*TradeCycleLogLifecycleTest" --tests "*SettingsSnapshotFreezeTest"`가 통과한다.
- 활성 인스턴스가 주기마다 사이클을 시작하고, 중복 실행 없이 로그를 남길 수 있다.

