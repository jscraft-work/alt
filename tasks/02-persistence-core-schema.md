# 02. 영속성 코어 스키마

목적:

- 운영 설정 도메인의 최소 스키마와 영속성 기반을 만든다.
- 이후 인증, 설정 CRUD, 워커가 같은 DB 기반 위에서 개발될 수 있게 한다.

선행조건:

- `01-spring-boot-bootstrap.md`

참조 문서:

- `docs/02-domain-model.md`
- `docs/03-database-schema.md`

이번 작업 범위:

- Flyway 설정 추가
- PostgreSQL + Testcontainers 테스트 기반 추가
- `app_user`
- `llm_model_profile`
- `system_parameter`
- `broker_account`
- `asset_master`
- `strategy_template`
- 공통 `created_at`, `updated_at`, `version`, `deleted_at` 규약 반영
- JPA 엔티티와 리포지토리 초안 추가

이번 작업에서 제외:

- `strategy_instance` 이하 실행 도메인
- 거래/판단/포트폴리오 테이블

필수 테스트 코드:

- `CoreSchemaFlywayTest`: 빈 DB에 Flyway가 끝까지 적용되는지 검증
- `CoreRepositoryMappingTest`: 핵심 엔티티 저장/조회가 가능한지 검증
- `StrategyTemplateConstraintTest`: `default_cycle_minutes` 등 check constraint가 동작하는지 검증
- `SoftDeleteVisibilityTest`: soft delete 대상이 기본 조회에서 제외되도록 repository 규칙을 검증

완료 조건:

- `./gradlew test --tests "*CoreSchemaFlywayTest" --tests "*CoreRepositoryMappingTest" --tests "*StrategyTemplateConstraintTest" --tests "*SoftDeleteVisibilityTest"`가 통과한다.
- 로컬 빈 PostgreSQL 기준 최초 마이그레이션이 재현 가능하다.

