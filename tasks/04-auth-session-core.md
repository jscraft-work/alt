# 04. 인증 세션 코어

목적:

- 운영자 로그인의 최소 기능을 구현한다.
- JWT 쿠키, CSRF, sliding session의 핵심 흐름을 먼저 닫는다.

선행조건:

- `03-persistence-trading-schema.md`

참조 문서:

- `spec2.md`
- `docs/04-api-spec.md`
- `docs/06-auth-security.md`

이번 작업 범위:

- `app_user` 기반 로그인
- `Argon2id` 비밀번호 해시
- JWT 발급/검증
- `ALT_ADMIN_SESSION` 쿠키 설정
- `XSRF-TOKEN` 발급
- `/api/auth/login`
- `/api/auth/logout`
- `/api/auth/me`
- `/api/auth/csrf`
- idle timeout 8시간
- absolute session 7일
- 남은 세션 2시간 이하 재발급

이번 작업에서 제외:

- 로그인 실패 차단
- reverse proxy client IP 판정
- 감사 로그 상세

필수 테스트 코드:

- `AuthLoginApiTest`: 정상 로그인 시 JWT/CSRF 쿠키와 응답 본문이 내려오는지 검증
- `AuthMeApiTest`: 유효 세션에서 `/api/auth/me`가 동작하는지 검증
- `AuthLogoutApiTest`: 로그아웃 시 세션 쿠키 만료가 내려오는지 검증
- `CsrfProtectionTest`: 상태 변경 요청이 CSRF 없이 차단되는지 검증
- `SlidingSessionTest`: 재발급 임계 구간에서 `Set-Cookie`가 갱신되는지 검증
- `AbsoluteSessionExpiryTest`: absolute max session 초과 시 `401`이 나는지 검증

완료 조건:

- `./gradlew test --tests "*AuthLoginApiTest" --tests "*AuthMeApiTest" --tests "*AuthLogoutApiTest" --tests "*CsrfProtectionTest" --tests "*SlidingSessionTest" --tests "*AbsoluteSessionExpiryTest"`가 통과한다.
- 브라우저 수동 확인 없이 MockMvc 또는 Spring Boot integration test로 로그인 플로우를 재현할 수 있다.

