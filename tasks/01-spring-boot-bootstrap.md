# 01. Spring Boot 부트스트랩

목적:

- 현재 순수 Java/Gradle 상태를 Spring Boot 3.x + Java 21 기반의 실행 가능한 백엔드 골격으로 전환한다.
- 이후 task가 공통으로 의존할 웹, 설정, 시간대, 헬스체크 기반을 만든다.

선행조건:

- 없음

참조 문서:

- `spec2.md`
- `docs/01-system-architecture.md`
- `docs/06-auth-security.md`

이번 작업 범위:

- `build.gradle.kts`를 Spring Boot, Actuator, Validation, Testcontainers 기반으로 정리
- `web-app`, `trading-worker`, `collector-worker`를 profile 또는 entrypoint 기준으로 분리할 실행 방식 결정
- `Asia/Seoul` 고정 time 설정
- 공통 API 에러 포맷 골격 추가
- `/actuator/health` 노출
- `.env.example`와 `ConfigurationProperties` 골격 추가
- PostgreSQL/Redis 연결 프로퍼티 이름만 먼저 고정

이번 작업에서 제외:

- 실제 DB 마이그레이션
- 인증 로직
- 도메인 CRUD

필수 테스트 코드:

- `AltApplicationContextTest`: Spring context가 기본 profile에서 기동되는지 검증
- `HealthEndpointTest`: `/actuator/health`가 `200`과 기본 상태를 반환하는지 검증
- `KstClockConfigTest`: 애플리케이션 기본 timezone이 `Asia/Seoul`인지 검증
- `AppPropertiesBindingTest`: 필수 설정 클래스가 정상 바인딩되는지 검증

완료 조건:

- `./gradlew test --tests "*AltApplicationContextTest" --tests "*HealthEndpointTest" --tests "*KstClockConfigTest" --tests "*AppPropertiesBindingTest"`가 통과한다.
- 이후 task에서 별도 부트스트랩 수정 없이 Spring Boot 테스트를 계속 추가할 수 있다.

