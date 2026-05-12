# 06. 운영자 기준정보 CRUD

목적:

- 전략 인스턴스 생성 전에 필요한 기준정보를 먼저 닫는다.
- 이후 task에서 템플릿/인스턴스/워커가 참조할 마스터 데이터를 안정화한다.

선행조건:

- `05-auth-blocking-and-audit.md`

참조 문서:

- `spec2.md`
- `docs/02-domain-model.md`
- `docs/04-api-spec.md`

이번 작업 범위:

- 전략 템플릿 CRUD
- LLM 모델 프로필 CRUD
- 시스템 파라미터 CRUD
- 종목 마스터 CRUD
- 브로커 계좌 CRUD
- 운영자 전용 API 인증/인가 적용
- 수정 API `version` 기반 optimistic lock
- 변경 감사 로그 기록

이번 작업에서 제외:

- 전략 인스턴스 CRUD
- 프롬프트 버전 이력
- 감시 종목 관리

필수 테스트 코드:

- `StrategyTemplateAdminApiTest`: 생성/수정/조회/삭제가 동작하는지 검증
- `ModelProfileAdminApiTest`: 목적별 모델 프로필 CRUD와 유효성 검증
- `SystemParameterAdminApiTest`: 파라미터 CRUD와 version 충돌 검증
- `AssetMasterAdminApiTest`: 종목 마스터 CRUD와 soft delete 동작 검증
- `BrokerAccountAdminApiTest`: 계좌 유니크 제약과 마스킹 필드 유지 검증
- `AdminOptimisticLockApiTest`: 오래된 `version`으로 수정 시 `409`가 나는지 검증

완료 조건:

- `./gradlew test --tests "*StrategyTemplateAdminApiTest" --tests "*ModelProfileAdminApiTest" --tests "*SystemParameterAdminApiTest" --tests "*AssetMasterAdminApiTest" --tests "*BrokerAccountAdminApiTest" --tests "*AdminOptimisticLockApiTest"`가 통과한다.
- 인스턴스 생성에 필요한 기준정보를 모두 API로 준비할 수 있다.

