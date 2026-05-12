# 03. 실행 도메인 스키마

목적:

- 전략 인스턴스와 실행 기록을 담는 나머지 핵심 스키마를 완성한다.
- 이후 API와 워커가 같은 영속 모델을 재사용할 수 있게 한다.

선행조건:

- `02-persistence-core-schema.md`

참조 문서:

- `docs/02-domain-model.md`
- `docs/03-database-schema.md`
- `docs/05-trading-cycle-design.md`

이번 작업 범위:

- `strategy_instance`
- `strategy_instance_prompt_version`
- `strategy_instance_watchlist_relation`
- `portfolio`
- `portfolio_position`
- `trade_cycle_log`
- `trade_decision_log`
- `trade_order_intent`
- `trade_order`
- `audit_log`
- `ops_event`
- optimistic lock 컬럼과 주요 unique/index 반영

이번 작업에서 제외:

- 실제 주문 실행 로직
- 조회 API

필수 테스트 코드:

- `TradingSchemaFlywayTest`: 누적 마이그레이션이 깨지지 않는지 검증
- `StrategyInstanceRepositoryTest`: 인스턴스와 프롬프트/감시종목 연관 저장이 되는지 검증
- `WatchlistUniqueConstraintTest`: soft delete partial unique가 동작하는지 검증
- `OptimisticLockPersistenceTest`: `version` 기반 충돌이 재현되는지 검증

완료 조건:

- `./gradlew test --tests "*TradingSchemaFlywayTest" --tests "*StrategyInstanceRepositoryTest" --tests "*WatchlistUniqueConstraintTest" --tests "*OptimisticLockPersistenceTest"`가 통과한다.
- `docs/03` 기준 핵심 운영 테이블이 모두 실제 마이그레이션으로 존재한다.

