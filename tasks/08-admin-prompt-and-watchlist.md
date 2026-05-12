# 08. 프롬프트 버전과 감시 종목 관리

목적:

- 인스턴스별 운영 설정 중 변동이 많은 프롬프트/감시 종목 관리를 분리해서 완성한다.
- 이후 트레이딩 워커가 신뢰할 수 있는 설정 스냅샷을 구성할 수 있게 한다.

선행조건:

- `07-admin-strategy-instance-lifecycle.md`

참조 문서:

- `spec2.md`
- `docs/02-domain-model.md`
- `docs/04-api-spec.md`

이번 작업 범위:

- 인스턴스 프롬프트 버전 생성
- 현재 버전 전환
- 이전 버전 복원
- 버전 번호 증가 규칙
- 감시 종목 추가/삭제
- soft delete 후 재추가 허용
- 특정 인스턴스 기준 감시 종목 목록 조회
- `dart_corp_code` 조회 보조 API 또는 서비스

이번 작업에서 제외:

- 트레이딩 워커가 프롬프트를 소비하는 로직
- DART 수집기 구현

필수 테스트 코드:

- `PromptVersionAdminApiTest`: 새 버전 생성과 현재 버전 전환 검증
- `PromptRestoreTest`: 과거 버전 복원 시 새 버전으로 다시 저장되는지 검증
- `WatchlistAdminApiTest`: 감시 종목 추가/삭제/재추가 검증
- `WatchlistUniqueReuseTest`: soft delete 후 동일 종목 재등록 가능 여부 검증
- `DartCorpCodeLookupTest`: 종목별 DART 코드 조회 규칙 검증

완료 조건:

- `./gradlew test --tests "*PromptVersionAdminApiTest" --tests "*PromptRestoreTest" --tests "*WatchlistAdminApiTest" --tests "*WatchlistUniqueReuseTest" --tests "*DartCorpCodeLookupTest"`가 통과한다.
- 프롬프트 복원 기능이 테스트 코드로 닫힌다.

