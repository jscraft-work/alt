# ALT-Web 개발 문서 계획

## 목적

이 디렉터리는 [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md)를 구현 가능한 개발 문서로 분해한 결과물을 관리한다.
기획서가 제품 범위와 운영 규칙을 정의한다면, 여기의 문서는 Java/Spring Boot 기반 구현을 위한 설계 기준을 정의한다.

## 작성 원칙

- 문서는 "현재 코드 설명"이 아니라 "구현 착수 기준"을 목표로 작성한다.
- 기획 요구사항과 설계 결정을 분리한다.
- 화면 중심 문서보다 도메인, 데이터, 운영 흐름을 우선 확정한다.
- 미확정 항목은 숨기지 않고 각 문서의 `Open Issues` 섹션에 남긴다.

## 보조 문서

- [구현 순서 및 체크리스트](/Users/jongsoobae/workspace/alt-java/docs/00-implementation-checklist.md)
  - 목적: 실제 개발 착수 순서와 단계별 완료 기준을 제공한다.

## 문서 목록과 순서

### 1. 시스템 아키텍처

- 파일: `docs/01-system-architecture.md`
- 목적: 전체 컴포넌트 경계, 실행 프로세스, 패키지/모듈 구조, 외부 연동 위치를 정의한다.
- 산출물 성격: 이후 모든 상세 설계의 상위 기준 문서

### 2. 도메인 모델

- 파일: `docs/02-domain-model.md`
- 목적: 전략 템플릿, 전략 인스턴스, 판단, 주문, 보유, 사이클 실행 로그 등의 개념과 상태 전이, 관계를 정의한다.
- 산출물 성격: 서비스 계층과 엔티티 설계의 기준 문서

### 3. 데이터베이스 스키마

- 파일: `docs/03-database-schema.md`
- 목적: 테이블, 컬럼, FK, 인덱스, soft delete, optimistic lock, 보존 정책 반영 구조를 정의한다.
- 산출물 성격: JPA 매핑과 마이그레이션 설계 기준 문서

### 4. API 명세

- 파일: `docs/04-api-spec.md`
- 목적: 대시보드, 매매이력, 뉴스/공시, 차트, 설정, 로그인에 필요한 API 계약을 정의한다.
- 산출물 성격: 프론트엔드 연동 및 컨트롤러/DTO 설계 기준 문서

### 5. 트레이딩 사이클 설계

- 파일: `docs/05-trading-cycle-design.md`
- 목적: 주기형 판단 실행, 스냅샷 고정, 스킵 정책, reconcile, auto_paused 처리, 비용 상한 처리 흐름을 정의한다.
- 산출물 성격: 스케줄러와 트레이딩 코어 구현 기준 문서

### 6. 인증 및 보안 설계

- 파일: `docs/06-auth-security.md`
- 목적: 운영자 로그인, JWT, IP 차단, reverse proxy 원본 IP 해석, secret 관리 정책을 정의한다.
- 산출물 성격: Spring Security 및 운영 보안 기준 문서

### 7. 외부 연동 설계

- 파일: `docs/07-external-integrations.md`
- 목적: 브로커 API, 시세/ws, 뉴스, DART, LLM 연동 인터페이스와 장애 처리 정책을 정의한다.
- 산출물 성격: 인프라 어댑터와 연동 테스트 기준 문서

### 8. 운영 설정 설계

- 파일: `docs/08-admin-config-rules.md`
- 목적: 템플릿/인스턴스 복제, 수정 가능 범위, optimistic lock 충돌 처리, 다음 사이클 반영 규칙을 정의한다.
- 산출물 성격: 설정 UI/API 및 정책 검증 기준 문서

### 9. 관측성 및 운영 런북

- 파일: `docs/09-observability-runbook.md`
- 목적: 로그, 메트릭, 알림, 운영 이벤트, 수동 해제 절차를 정의한다.
- 산출물 성격: 운영 대응 및 장애 분석 기준 문서

### 10. 테스트 전략

- 파일: `docs/10-test-strategy.md`
- 목적: 단위/통합/계약/스케줄러/시뮬레이션 테스트 범위를 정의한다.
- 산출물 성격: 품질 검증 기준 문서

## 우선 확정 대상

구현 시작 전에 아래 5개 문서를 먼저 확정한다.

1. 시스템 아키텍처
2. 도메인 모델
3. 데이터베이스 스키마
4. API 명세
5. 트레이딩 사이클 설계

## 현재 상태

- 완료: `01-system-architecture.md`
- 완료: `02-domain-model.md`
- 완료: `03-database-schema.md`
- 완료: `04-api-spec.md`
- 완료: `05-trading-cycle-design.md`
- 완료: `06-auth-security.md`
- 완료: `07-external-integrations.md`
- 완료: `08-admin-config-rules.md`
- 완료: `09-observability-runbook.md`
- 완료: `10-test-strategy.md`

기획서 분해 문서는 v1 범위에서 모두 작성 완료. 구현 진행 상태는 [00-implementation-checklist.md](/Users/jongsoobae/workspace/alt-java/docs/00-implementation-checklist.md)에서 관리한다.
