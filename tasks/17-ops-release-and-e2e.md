# 17. 운영 마감, 배포, 최종 E2E

목적:

- 개별 기능 구현 이후 운영 가능 수준으로 마감한다.
- 로깅, 알림, 배포, seed, 최종 통합 테스트를 이 task에서 정리한다.

선행조건:

- `16-live-order-execution.md`

참조 문서:

- `docs/01-system-architecture.md`
- `docs/06-auth-security.md`
- `docs/07-external-integrations.md`

이번 작업 범위:

- 구조 로그와 JSON 로그 포맷 정리
- `ops_event` 보존/표시 정책 정리
- Telegram 알림 on/off 및 음소거 시간대
- health/readiness 점검 보강
- 로컬/운영 profile 정리
- 기본 seed 데이터와 최초 운영자 생성 절차
- GitHub Secrets -> 서버 `.env` 주입 절차 문서화
- 배포 webhook 또는 배포 엔드포인트 보강
- 핵심 플로우 통합 테스트 추가

필수 테스트 코드:

- `OperationalLoggingTest`: 주요 이벤트가 구조 로그/마스킹 규칙을 지키는지 검증
- `TelegramAlertPolicyTest`: 알림 on/off와 음소거 시간대 정책 검증
- `ReadinessHealthTest`: DB/Redis/핵심 의존성 readiness 검증
- `SeedDataBootstrapTest`: 기본 seed와 운영자 초기화 절차 검증
- `PaperWorkflowSmokeE2ETest`: 로그인 -> 설정 -> 수집 더블 -> paper 사이클 -> 조회 API까지 스모크 검증
- `FailurePathRegressionTest`: 인증 실패, 수집 실패, LLM 실패, reconcile 실패의 회귀 검증

완료 조건:

- `./gradlew test --tests "*OperationalLoggingTest" --tests "*TelegramAlertPolicyTest" --tests "*ReadinessHealthTest" --tests "*SeedDataBootstrapTest" --tests "*PaperWorkflowSmokeE2ETest" --tests "*FailurePathRegressionTest"`가 통과한다.
- 추가 수동 완료 조건: 운영 배포 문서와 시크릿 주입 절차가 실제 서버 기준으로 한 번 검증되어야 한다.

