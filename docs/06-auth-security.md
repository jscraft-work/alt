# 06. 인증 및 보안 설계

## 1. 문서 목적과 범위

이 문서는 [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md), [01-system-architecture.md](/Users/jongsoobae/workspace/alt-java/docs/01-system-architecture.md), [04-api-spec.md](/Users/jongsoobae/workspace/alt-java/docs/04-api-spec.md), [05-trading-cycle-design.md](/Users/jongsoobae/workspace/alt-java/docs/05-trading-cycle-design.md)를 기준으로 운영자 인증과 웹 보안 규칙을 구현 가능한 수준으로 고정한다.

목표:

- 운영자 인증 방식과 세션 정책 고정
- 쿠키/JWT/CSRF 규칙 고정
- reverse proxy 기반 원본 IP 판정 규칙 고정
- 로그인 실패 차단, secret 관리, 감사 로그 기준 고정

비범위:

- 브로커/LLM 자격 증명 상세 포맷
- 인프라 방화벽 설정 상세
- WAF/CDN 설정 상세

## 2. 보호 대상과 경계

보호 대상:

- `/api/admin/**`
- `/api/auth/logout`
- 향후 추가되는 운영자 전용 편집/관리 API

비로그인 허용 대상:

- 대시보드 조회
- 매매이력 조회
- 뉴스·공시 조회
- 차트 조회
- 로그인 화면과 로그인 요청

권한 경계:

- 웹 운영자 세션과 트레이딩 워커 권한은 분리한다.
- 워커는 사용자 JWT를 사용하지 않는다.
- 브로커/LLM/수집기 자격 증명은 서비스 계정 시크릿으로 분리한다.

## 3. 운영자 계정 모델

- 운영자 계정은 DB의 `app_user` 테이블로 관리한다.
- 회원가입, 공개 사용자 생성, 비밀번호 재설정 화면은 제공하지 않는다.
- 초기 운영자 계정은 마이그레이션 또는 별도 시드 절차로 넣는다.
- 런타임에 평문 비밀번호를 환경변수에 두고 인증하지 않는다.

최소 컬럼:

- `login_id`
- `password_hash`
- `display_name`
- `role_code`
- `enabled`

비밀번호 규칙:

- 저장은 해시만 허용
- 해시 알고리즘은 `Argon2id`를 기본으로 사용
- 운영상 불가하면 `bcrypt`를 차선으로 허용

역할:

- v1은 `ADMIN` 단일 역할만 사용한다.

## 4. 세션과 JWT 정책

### 4.1 기본 정책

- 인증 방식은 JWT 기반 세션 쿠키다.
- refresh token은 사용하지 않는다.
- 세션은 sliding 방식으로 연장한다.

세션 값:

- idle timeout: 8시간
- absolute max session: 7일
- 재발급 임계: 남은 세션 2시간 이하일 때

### 4.2 JWT 저장 방식

JWT는 응답 본문이 아니라 쿠키로 전달한다.

쿠키 규칙:

- 이름: `ALT_ADMIN_SESSION`
- `HttpOnly`
- `Secure`
- `SameSite=Lax`
- `Path=/`

비고:

- 운영 환경에서 HTTPS를 강제한다.
- 개발 환경의 로컬 HTTP 예외는 로컬 프로파일에서만 허용한다.

### 4.3 JWT 클레임

최소 클레임:

- `sub`: 사용자 ID
- `login_id`
- `role_code`
- `iat`
- `exp`
- `session_started_at`

설명:

- `exp`는 현재 JWT의 만료 시각
- `session_started_at`은 absolute max session 계산 기준
- 서버는 별도 세션 저장소 조회 없이 JWT 자체만 검증한다.

### 4.4 재발급 규칙

인증된 요청이 들어왔을 때:

- JWT가 유효하고
- absolute max session을 넘지 않았고
- 남은 유효 시간이 2시간 이하이면

서버는 새 JWT를 발급하고 `Set-Cookie`로 교체한다.

absolute max session을 넘긴 경우:

- 재발급하지 않는다.
- `401`을 반환하고 재로그인을 요구한다.

### 4.5 로그아웃

로그아웃 시:

- 서버는 인증 쿠키를 즉시 만료시킨다.
- 별도 refresh token 폐기 로직은 없다.
- 이미 발급된 JWT는 만료 시각 전까지 유효할 수 있으므로 idle timeout 8시간, absolute max session 7일 제한으로 운영한다.

## 5. CSRF 정책

쿠키 기반 인증을 사용하므로 CSRF 방어를 적용한다.

기본 규칙:

- `GET`, `HEAD`, `OPTIONS`는 CSRF 검사 대상에서 제외
- 상태를 바꾸는 요청은 CSRF 토큰을 요구
- 적용 대상:
  - `/api/admin/**`
  - `/api/auth/logout`

방식:

- double-submit cookie 방식을 사용
- CSRF 쿠키 이름: `XSRF-TOKEN`
- 요청 헤더 이름: `X-CSRF-TOKEN`

토큰 발급:

- 로그인 성공 시 JWT 쿠키와 함께 발급
- 필요 시 `GET /api/auth/csrf`로 재발급 가능

비고:

- `XSRF-TOKEN` 쿠키는 JS에서 읽을 수 있어야 하므로 `HttpOnly`를 사용하지 않는다.
- CSRF 토큰 불일치 시 `403`을 반환한다.

## 6. 원본 IP 판정 규칙

로그인 실패 차단과 감사 로그의 기준 IP는 reverse proxy 신뢰 체인을 바탕으로 계산한다.

입력 값:

- `remoteAddr`
- `X-Forwarded-For`

설정:

- `TRUSTED_PROXY_CIDRS`

판정 규칙:

1. `remoteAddr`가 신뢰 프록시 대역이 아니면 `remoteAddr`를 client IP로 사용한다.
2. `remoteAddr`가 신뢰 프록시 대역이고 `X-Forwarded-For`가 있으면 체인을 파싱한다.
3. 체인은 오른쪽에서 왼쪽으로 검사하며, 신뢰 프록시 대역을 제거한다.
4. 마지막으로 남는 첫 번째 비신뢰 IP를 client IP로 사용한다.
5. 유효한 IP를 판정할 수 없으면 `remoteAddr`로 fallback 한다.

비고:

- 헤더를 무조건 신뢰하지 않는다.
- 신뢰 프록시 목록은 운영 환경에서 명시적으로 관리한다.

## 7. 로그인 실패 차단

저장소:

- Redis

키 예시:

- 실패 카운트: `auth:login_fail:{clientIp}`
- 차단 상태: `auth:login_block:{clientIp}`

정책:

- 5회 실패 시 5분 차단
- 실패 카운트 TTL도 5분
- 로그인 성공 시 해당 IP의 실패 카운트 초기화

관리 기능:

- 운영자는 특정 IP 차단을 수동 해제할 수 있어야 한다.
- 해제 기능은 운영자 전용 API로 제공한다.

## 8. 인증/인가 흐름

### 8.1 로그인

1. `login_id`, `password` 수신
2. client IP 판정
3. 차단 여부 확인
4. `app_user.enabled=true` 계정 조회
5. 비밀번호 해시 검증
6. 성공 시 JWT + CSRF 토큰 발급
7. 실패 시 실패 카운트 증가
8. 감사 로그 기록

### 8.2 보호 API 접근

1. JWT 쿠키 존재 확인
2. JWT 서명/만료 검증
3. absolute max session 검증
4. 역할 검증
5. 필요 시 CSRF 검증
6. sliding 재발급 조건 확인

## 9. secret 관리

시크릿 예시:

- JWT signing key
- 브로커 API key/secret
- LLM 실행 키
- Telegram bot token
- DB 비밀번호

원칙:

- 민감 값은 GitHub Secrets로 관리하고, 배포 시 서버의 `.env`로 주입
- Git 저장소 커밋 금지
- 로그 출력 금지
- 예외 메시지에 평문 노출 금지
- 운영/개발 시크릿 분리

## 10. 응답과 에러 처리

인증/인가 실패:

- 미인증: `401`
- 권한 부족: `403`
- CSRF 실패: `403`
- 로그인 실패 횟수 초과 차단: `429`

비고:

- 대시보드 자동 갱신의 `401`은 전체 화면 리다이렉트 대신 인라인 알림으로 처리한다.

## 11. 감사 로그 기준

다음 사건은 `audit_log`에 남긴다.

- 로그인 성공
- 로그인 실패
- 로그아웃
- IP 차단 발생
- IP 차단 수동 해제
- 운영자 계정 활성/비활성 변경
- 운영자 비밀번호 변경

최소 필드:

- 발생 시각
- actor
- client IP
- action type
- 결과(success/failure)

## 12. 보안 헤더와 브라우저 정책

운영 환경 기본 헤더:

- `Strict-Transport-Security`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- 적절한 `Content-Security-Policy`

비고:

- CSP는 React + Vite 산출물 기준으로 별도 조정한다.

## 13. 구현 체크리스트

- `app_user` 기반 운영자 인증
- `Argon2id` 비밀번호 해시
- JWT 쿠키 + sliding session
- absolute max session 7일
- CSRF double-submit cookie
- Redis 로그인 실패 카운트/차단
- reverse proxy 신뢰 체인 기반 client IP 판정
- 감사 로그 기록
- 보안 헤더 적용
