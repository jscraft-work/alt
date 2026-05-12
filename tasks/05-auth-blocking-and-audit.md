# 05. 인증 차단과 감사 로그

목적:

- 인증을 운영 수준으로 끌어올린다.
- 로그인 실패 차단, 신뢰 프록시 체인 해석, 인증 감사 로그를 닫는다.

선행조건:

- `04-auth-session-core.md`

참조 문서:

- `spec2.md`
- `docs/06-auth-security.md`

이번 작업 범위:

- Redis 기반 로그인 실패 카운트
- 5회 실패 시 5분 차단
- 성공 로그인 시 실패 카운트 초기화
- `X-Forwarded-For` + `TRUSTED_PROXY_CIDRS` 기반 client IP 판정
- 인증 성공/실패/로그아웃 감사 로그 기록
- 운영자 차단 해제 API

이번 작업에서 제외:

- 다른 운영자 설정 CRUD
- 워커 권한

필수 테스트 코드:

- `TrustedProxyIpResolverTest`: reverse proxy 체인 파싱 규칙을 케이스별로 검증
- `LoginFailureBlockingTest`: 5회 실패 후 차단과 TTL이 동작하는지 검증
- `LoginFailureResetTest`: 성공 로그인 후 실패 카운트가 초기화되는지 검증
- `AuthAuditLogTest`: 로그인 성공/실패/로그아웃 이벤트가 `audit_log`에 남는지 검증
- `AuthBlockReleaseApiTest`: 운영자 차단 해제 API가 Redis 상태를 해제하는지 검증

완료 조건:

- `./gradlew test --tests "*TrustedProxyIpResolverTest" --tests "*LoginFailureBlockingTest" --tests "*LoginFailureResetTest" --tests "*AuthAuditLogTest" --tests "*AuthBlockReleaseApiTest"`가 통과한다.
- `docs/06`의 로그인 차단 규칙이 테스트 코드로 재현된다.

