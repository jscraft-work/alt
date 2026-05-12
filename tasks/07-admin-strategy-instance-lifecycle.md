# 07. 전략 인스턴스 CRUD와 상태 전이

목적:

- 실제 운영 단위인 `strategy_instance` 관리 기능을 닫는다.
- `draft / active / inactive` 전이와 활성화 전 검증을 이 task에서 끝낸다.

선행조건:

- `06-admin-catalog-crud.md`

참조 문서:

- `spec2.md`
- `docs/02-domain-model.md`
- `docs/04-api-spec.md`

이번 작업 범위:

- 전략 인스턴스 CRUD
- 템플릿 기준 초기값 복제
- `draft / active / inactive` 상태 전이
- 활성화 전 검증
- `paper/live` 모드 검증
- live 계좌 연결 검증
- 같은 시점 `active live` 인스턴스의 계좌 중복 사용 방지
- 인스턴스 변경 감사 로그 기록

활성화 전 검증 기준:

- 현재 프롬프트 존재
- 트레이딩 모델 존재
- 입력 스펙 존재
- 실행 주기 존재
- 예산 존재
- `live`면 연결 계좌 존재

이번 작업에서 제외:

- 프롬프트 버전 히스토리 편집
- 감시 종목 CRUD

필수 테스트 코드:

- `StrategyInstanceAdminApiTest`: 인스턴스 생성/수정/조회/비활성화 검증
- `StrategyInstanceActivationTest`: 조건 미충족 시 `INSTANCE_NOT_ACTIVATABLE` 검증
- `LiveBrokerAccountExclusivityTest`: 동일 계좌 중복 활성화 방지 검증
- `StrategyInstanceLifecycleTransitionTest`: 허용/비허용 상태 전이 검증
- `StrategyInstanceOptimisticLockTest`: version 충돌 시 `409` 검증

완료 조건:

- `./gradlew test --tests "*StrategyInstanceAdminApiTest" --tests "*StrategyInstanceActivationTest" --tests "*LiveBrokerAccountExclusivityTest" --tests "*StrategyInstanceLifecycleTransitionTest" --tests "*StrategyInstanceOptimisticLockTest"`가 통과한다.
- 템플릿 생성 후 인스턴스를 만들고 활성화하는 흐름이 API 테스트로 닫힌다.

