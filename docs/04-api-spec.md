# 04. API 명세

## 1. 문서 목적과 범위

이 문서는 [spec2.md](/Users/jongsoobae/workspace/alt-java/spec2.md), [01-system-architecture.md](/Users/jongsoobae/workspace/alt-java/docs/01-system-architecture.md), [02-domain-model.md](/Users/jongsoobae/workspace/alt-java/docs/02-domain-model.md), [03-database-schema.md](/Users/jongsoobae/workspace/alt-java/docs/03-database-schema.md)를 기준으로 웹 프론트엔드와 백엔드 사이의 HTTP API 계약을 정의한다.

목표:

- 화면별 필요한 조회/편집 API 목록 고정
- 인증 필요 여부와 권한 경계 고정
- 요청/응답 DTO 기준선 고정
- optimistic lock, 페이징, 필터링, 시간 표현 규칙 고정

비범위:

- 내부 서비스 메서드 시그니처
- 외부 API 연동 DTO 상세
- OpenAPI 생성 코드
- WebSocket/SSE 계약

## 2. 공통 규칙

### 2.1 기본 규칙

- Base path는 `/api`다.
- 응답 본문은 JSON만 사용한다.
- 시간 값은 모두 KST 기준 ISO-8601 문자열을 사용한다.
- 날짜 값은 `YYYY-MM-DD` 문자열을 사용한다.
- 목록 조회는 기본적으로 최신순 정렬을 사용한다.
- 프론트는 대시보드 자동 갱신을 polling으로 수행한다.

### 2.2 인증 규칙

- 비로그인 사용자는 조회 전용 API만 호출할 수 있다.
- 운영자 전용 API는 JWT 인증 쿠키가 필요하다.
- 미인증 상태에서 운영자 API 호출 시 `401`을 반환한다.
- 권한은 있으나 접근 범위를 벗어나면 `403`을 반환한다.
- refresh token은 사용하지 않는다.
- 인증 쿠키는 `HttpOnly + Secure + SameSite` 속성을 사용한다.
- 세션은 sliding 방식으로 연장한다. idle timeout은 8시간, absolute max session은 7일이다.
- 인증된 요청 시 남은 세션 시간이 짧으면 서버는 새 JWT 쿠키를 다시 내려준다.

### 2.3 응답 형식

단건/목록 조회는 아래 형식을 기본으로 사용한다.

```json
{
  "data": {},
  "meta": {}
}
```

에러 응답 형식:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다.",
    "fieldErrors": [
      {
        "field": "cycleMinutes",
        "message": "1 이상 30 이하만 허용됩니다."
      }
    ]
  }
}
```

에러 코드 예시:

- `UNAUTHORIZED`
- `FORBIDDEN`
- `TOO_MANY_REQUESTS`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `OPTIMISTIC_LOCK_CONFLICT`
- `INSTANCE_NOT_ACTIVATABLE`
- `BROKER_ACCOUNT_ALREADY_IN_USE`

### 2.4 optimistic lock 규칙

- 수정 API는 요청 본문에 `version`을 포함한다.
- 현재 저장값과 요청 `version`이 다르면 `409 CONFLICT`를 반환한다.
- 충돌 응답은 최신 요약값을 함께 줄 수 있다.

```json
{
  "error": {
    "code": "OPTIMISTIC_LOCK_CONFLICT",
    "message": "다른 사용자가 먼저 수정했습니다."
  },
  "meta": {
    "currentVersion": 12
  }
}
```

### 2.5 페이징 규칙

- 목록 조회는 `page`, `size`를 사용한다.
- 기본값은 `page=1`, `size=20`이다.
- 최대 `size`는 `100`이다.

```json
{
  "data": [],
  "meta": {
    "page": 1,
    "size": 20,
    "totalElements": 132,
    "totalPages": 7
  }
}
```

## 3. 인증 API

### 3.1 로그인

`POST /api/auth/login`

- 인증: 불필요
- 목적: 운영자 로그인

요청:

```json
{
  "loginId": "admin",
  "password": "********"
}
```

응답:

```json
{
  "data": {
    "expiresAt": "2026-05-06T14:30:00+09:00",
    "user": {
      "id": "uuid",
      "loginId": "admin",
      "displayName": "관리자",
      "roleCode": "ADMIN"
    }
  }
}
```

비고:

- 로그인 실패 5회/5분 IP 차단 규칙은 auth/security 구현에서 적용한다.
- JWT는 응답 본문이 아니라 `Set-Cookie` 헤더로 내려준다.

### 3.2 로그아웃

`POST /api/auth/logout`

- 인증: 필요
- 목적: 클라이언트 토큰 폐기 처리

응답:

```json
{
  "data": {
    "success": true
  }
}
```

비고:

- 서버는 인증 쿠키를 즉시 만료시키는 `Set-Cookie`를 함께 내려준다.

### 3.3 현재 로그인 사용자

`GET /api/auth/me`

- 인증: 필요

응답:

```json
{
  "data": {
    "id": "uuid",
    "loginId": "admin",
    "displayName": "관리자",
    "roleCode": "ADMIN"
  }
}
```

### 3.4 CSRF 토큰 조회

`GET /api/auth/csrf`

- 인증: 불필요
- 목적: CSRF 토큰 쿠키 발급 또는 재발급

응답:

```json
{
  "data": {
    "success": true
  }
}
```

비고:

- 서버는 `XSRF-TOKEN` 쿠키를 함께 내려준다.

## 4. 대시보드 API

### 4.1 전략 오버뷰

`GET /api/dashboard/strategy-overview`

- 인증: 불필요
- 목적: 전체 전략 인스턴스 카드 조회

쿼리:

- `lifecycleState`: optional
- `autoPaused`: optional boolean

응답:

```json
{
  "data": [
    {
      "strategyInstanceId": "uuid",
      "name": "KR 모멘텀 A",
      "executionMode": "paper",
      "lifecycleState": "active",
      "autoPausedReason": null,
      "budgetAmount": 10000000,
      "cashAmount": 6200000,
      "totalAssetAmount": 10120000,
      "todayRealizedPnl": 120000,
      "latestDecisionStatus": "HOLD",
      "latestDecisionAt": "2026-05-06T10:05:00+09:00",
      "watchlistCount": 8
    }
  ]
}
```

### 4.2 인스턴스 대시보드

`GET /api/dashboard/instances/{strategyInstanceId}`

- 인증: 불필요
- 목적: 인스턴스 대시보드 상세 조회

응답:

```json
{
  "data": {
    "instance": {
      "id": "uuid",
      "name": "KR 모멘텀 A",
      "executionMode": "paper",
      "lifecycleState": "active",
      "autoPausedReason": null,
      "budgetAmount": 10000000,
      "brokerAccountMasked": null
    },
    "portfolio": {
      "cashAmount": 6200000,
      "totalAssetAmount": 10120000,
      "realizedPnlToday": 120000
    },
    "positions": [
      {
        "symbolCode": "005930",
        "symbolName": "삼성전자",
        "quantity": 12,
        "avgBuyPrice": 81200,
        "lastMarkPrice": 82600,
        "unrealizedPnl": 16800
      }
    ],
    "systemStatus": [
      {
        "serviceName": "marketdata",
        "statusCode": "ok",
        "message": null,
        "occurredAt": "2026-05-06T10:05:00+09:00"
      }
    ],
    "latestDecision": {
      "decisionLogId": "uuid",
      "cycleStatus": "HOLD",
      "summary": "관망 유지",
      "cycleStartedAt": "2026-05-06T10:05:00+09:00"
    },
    "recentOrders": [
      {
        "tradeOrderId": "uuid",
        "symbolCode": "005930",
        "side": "BUY",
        "orderStatus": "filled",
        "requestedAt": "2026-05-06T09:35:01+09:00",
        "requestedQuantity": 5,
        "requestedPrice": 81000
      }
    ]
  }
}
```

### 4.3 대시보드 상태 요약

`GET /api/dashboard/system-status`

- 인증: 불필요
- 목적: 상단 또는 전체 화면 공통 상태 카드 계산용

응답:

```json
{
  "data": [
    {
      "serviceName": "marketdata",
      "statusCode": "ok",
      "message": null,
      "lastSuccessAt": "2026-05-06T10:05:00+09:00"
    },
    {
      "serviceName": "news",
      "statusCode": "delayed",
      "message": "최근 수집 주기 지연",
      "lastSuccessAt": "2026-05-06T09:58:00+09:00"
    }
  ]
}
```

## 5. 매매이력 API

### 5.1 주문 이력 목록

`GET /api/trade-orders`

- 인증: 불필요

쿼리:

- `strategyInstanceId`: optional
- `symbolCode`: optional
- `orderStatus`: optional
- `dateFrom`: optional
- `dateTo`: optional
- `page`, `size`

응답:

```json
{
  "data": [
    {
      "id": "uuid",
      "strategyInstanceId": "uuid",
      "symbolCode": "005930",
      "side": "BUY",
      "executionMode": "paper",
      "orderStatus": "filled",
      "requestedQuantity": 5,
      "requestedPrice": 81000,
      "filledQuantity": 5,
      "avgFilledPrice": 81000,
      "requestedAt": "2026-05-06T09:35:01+09:00",
      "filledAt": "2026-05-06T09:35:01+09:00"
    }
  ],
  "meta": {
    "page": 1,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 5.2 주문 이력 상세

`GET /api/trade-orders/{tradeOrderId}`

- 인증: 불필요

응답:

```json
{
  "data": {
    "id": "uuid",
    "tradeOrderIntentId": "uuid",
    "strategyInstanceId": "uuid",
    "clientOrderId": "client-uuid",
    "brokerOrderNo": null,
    "executionMode": "paper",
    "orderStatus": "filled",
    "requestedQuantity": 5,
    "requestedPrice": 81000,
    "filledQuantity": 5,
    "avgFilledPrice": 81000,
    "requestedAt": "2026-05-06T09:35:01+09:00",
    "acceptedAt": "2026-05-06T09:35:01+09:00",
    "filledAt": "2026-05-06T09:35:01+09:00",
    "failureReason": null,
    "portfolioAfter": {
      "cashAmount": 6200000,
      "totalAssetAmount": 10120000,
      "positions": [
        {
          "symbolCode": "005930",
          "quantity": 12,
          "avgBuyPrice": 81200
        }
      ]
    }
  }
}
```

### 5.3 판단 로그 목록

`GET /api/trade-decisions`

- 인증: 불필요

쿼리:

- `strategyInstanceId`: optional
- `cycleStatus`: optional
- `dateFrom`: optional
- `dateTo`: optional
- `page`, `size`

응답:

```json
{
  "data": [
    {
      "id": "uuid",
      "strategyInstanceId": "uuid",
      "cycleStatus": "EXECUTE",
      "summary": "삼성전자 5주 매수",
      "confidence": 0.83,
      "failureReason": null,
      "cycleStartedAt": "2026-05-06T09:35:00+09:00",
      "cycleFinishedAt": "2026-05-06T09:35:02+09:00",
      "orderCount": 1
    }
  ],
  "meta": {
    "page": 1,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 5.4 판단 로그 상세

`GET /api/trade-decisions/{tradeDecisionLogId}`

- 인증: 불필요

응답:

```json
{
  "data": {
    "id": "uuid",
    "strategyInstanceId": "uuid",
    "cycleStatus": "EXECUTE",
    "summary": "삼성전자 5주 매수",
    "confidence": 0.83,
    "failureReason": null,
    "failureDetail": null,
    "cycleStartedAt": "2026-05-06T09:35:00+09:00",
    "cycleFinishedAt": "2026-05-06T09:35:02+09:00",
    "requestText": "LLM request...",
    "responseText": "LLM response...",
    "stdoutText": "stdout...",
    "stderrText": "",
    "inputTokens": 1821,
    "outputTokens": 226,
    "estimatedCost": 0.0213,
    "callStatus": "SUCCESS",
    "parsedDecision": {
      "cycleStatus": "EXECUTE",
      "summary": "삼성전자 5주 매수",
      "orders": [
        {
          "sequenceNo": 1,
          "symbolCode": "005930",
          "side": "BUY",
          "quantity": 5,
          "orderType": "LIMIT",
          "price": 81000,
          "rationale": "단기 반등 가능성",
          "evidence": [
            {
              "sourceType": "news",
              "title": "실적 개선 기대"
            }
          ]
        }
      ]
    },
    "settingsSnapshot": {
      "promptVersionId": "uuid",
      "tradingModelProfileId": "uuid",
      "inputSpec": {},
      "executionConfig": {},
      "watchlist": [
        "005930",
        "000660"
      ]
    },
    "orderIntents": [
      {
        "id": "uuid",
        "sequenceNo": 1,
        "symbolCode": "005930",
        "side": "BUY",
        "quantity": 5,
        "orderType": "LIMIT",
        "price": 81000,
        "rationale": "단기 반등 가능성",
        "evidence": [],
        "executionBlockedReason": null
      }
    ],
    "orders": [
      {
        "id": "uuid",
        "tradeOrderIntentId": "uuid",
        "orderStatus": "filled",
        "requestedAt": "2026-05-06T09:35:01+09:00"
      }
    ]
  }
}
```

## 6. 뉴스·공시 API

### 6.1 뉴스 목록

`GET /api/news`

- 인증: 불필요

쿼리:

- `symbolCode`: optional
- `strategyInstanceId`: optional
- `usefulnessStatus`: optional
- `dateFrom`: optional
- `dateTo`: optional
- `q`: optional
- `page`, `size`

응답:

```json
{
  "data": [
    {
      "id": "uuid",
      "providerName": "naver",
      "title": "삼성전자 실적 기대감",
      "articleUrl": "https://...",
      "publishedAt": "2026-05-06T08:40:00+09:00",
      "summary": "실적 기대감이 반영...",
      "usefulnessStatus": "useful",
      "relatedAssets": [
        {
          "symbolCode": "005930",
          "symbolName": "삼성전자"
        }
      ]
    }
  ],
  "meta": {
    "page": 1,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 6.2 공시 목록

`GET /api/disclosures`

- 인증: 불필요

쿼리:

- `symbolCode`: optional
- `dartCorpCode`: optional
- `strategyInstanceId`: optional
- `dateFrom`: optional
- `dateTo`: optional
- `page`, `size`

응답:

```json
{
  "data": [
    {
      "id": "uuid",
      "dartCorpCode": "00126380",
      "symbolCode": "005930",
      "symbolName": "삼성전자",
      "title": "주요사항보고서",
      "publishedAt": "2026-05-06T07:10:00+09:00",
      "previewText": "주요 공시 내용 요약...",
      "documentUrl": "https://dart.fss.or.kr/..."
    }
  ]
}
```

## 7. 차트 API

### 7.1 분봉 조회

`GET /api/charts/minutes`

- 인증: 불필요

쿼리:

- `symbolCode`: required
- `date`: required

응답:

```json
{
  "data": {
    "symbolCode": "005930",
    "date": "2026-05-06",
    "bars": [
      {
        "barTime": "2026-05-06T09:01:00+09:00",
        "openPrice": 81000,
        "highPrice": 81100,
        "lowPrice": 80900,
        "closePrice": 81050,
        "volume": 12031
      }
    ]
  }
}
```

### 7.2 차트 오버레이 주문 조회

`GET /api/charts/order-overlays`

- 인증: 불필요

쿼리:

- `symbolCode`: required
- `date`: required
- `strategyInstanceId`: optional

응답:

```json
{
  "data": [
    {
      "tradeOrderId": "uuid",
      "side": "BUY",
      "orderStatus": "filled",
      "requestedAt": "2026-05-06T09:35:01+09:00",
      "filledAt": "2026-05-06T09:35:01+09:00",
      "requestedPrice": 81000,
      "avgFilledPrice": 81000,
      "requestedQuantity": 5
    }
  ]
}
```

## 8. 설정 API

모든 설정 API는 인증이 필요하다.

### 8.1 전략 템플릿 목록

`GET /api/admin/strategy-templates`

응답:

```json
{
  "data": [
    {
      "id": "uuid",
      "name": "KR 모멘텀 템플릿",
      "description": "기본 모멘텀 전략",
      "defaultCycleMinutes": 5,
      "defaultTradingModelProfileId": "uuid",
      "version": 3,
      "updatedAt": "2026-05-06T09:00:00+09:00"
    }
  ]
}
```

### 8.2 전략 템플릿 생성

`POST /api/admin/strategy-templates`

요청:

```json
{
  "name": "KR 모멘텀 템플릿",
  "description": "기본 모멘텀 전략",
  "defaultCycleMinutes": 5,
  "defaultPromptText": "prompt...",
  "defaultInputSpec": {},
  "defaultExecutionConfig": {},
  "defaultTradingModelProfileId": "uuid"
}
```

### 8.3 전략 템플릿 수정

`PATCH /api/admin/strategy-templates/{strategyTemplateId}`

요청:

```json
{
  "name": "KR 모멘텀 템플릿 v2",
  "description": "수정 설명",
  "defaultCycleMinutes": 3,
  "defaultPromptText": "prompt...",
  "defaultInputSpec": {},
  "defaultExecutionConfig": {},
  "defaultTradingModelProfileId": "uuid",
  "version": 3
}
```

### 8.4 전략 인스턴스 목록

`GET /api/admin/strategy-instances`

쿼리:

- `lifecycleState`: optional
- `executionMode`: optional

### 8.5 전략 인스턴스 생성

`POST /api/admin/strategy-instances`

요청:

```json
{
  "strategyTemplateId": "uuid",
  "name": "KR 모멘텀 A",
  "executionMode": "paper",
  "brokerAccountId": null,
  "budgetAmount": 10000000,
  "tradingModelProfileId": null,
  "inputSpecOverride": {},
  "executionConfigOverride": {}
}
```

### 8.6 전략 인스턴스 수정

`PATCH /api/admin/strategy-instances/{strategyInstanceId}`

요청:

```json
{
  "name": "KR 모멘텀 A",
  "budgetAmount": 12000000,
  "brokerAccountId": null,
  "tradingModelProfileId": "uuid",
  "inputSpecOverride": {},
  "executionConfigOverride": {},
  "version": 7
}
```

비고:
- 생략한 필드는 기존 값을 유지한다.
- `brokerAccountId`, `tradingModelProfileId`, `inputSpecOverride`, `executionConfigOverride`에 `null`을 보내면 override 를 해제하고 템플릿 기본값을 사용한다.

### 8.7 전략 인스턴스 상태 전환

`POST /api/admin/strategy-instances/{strategyInstanceId}/lifecycle`

요청:

```json
{
  "targetState": "active",
  "version": 7
}
```

비고:

- 활성화 가능 조건을 만족하지 않으면 `INSTANCE_NOT_ACTIVATABLE`을 반환한다.

### 8.8 전략 인스턴스 복제

`POST /api/admin/strategy-instances/{strategyInstanceId}/duplicate`

요청:

```json
{
  "name": "KR 모멘텀 A 복제본"
}
```

비고:

- 복제 결과는 항상 `draft`로 생성한다.
- 자산, 판단 로그, 주문 이력은 복제하지 않는다.

### 8.9 프롬프트 버전 목록

`GET /api/admin/strategy-instances/{strategyInstanceId}/prompt-versions`

### 8.10 프롬프트 새 버전 생성

`POST /api/admin/strategy-instances/{strategyInstanceId}/prompt-versions`

요청:

```json
{
  "promptText": "new prompt...",
  "changeNote": "뉴스 입력 강화"
}
```

### 8.11 프롬프트 버전 재적용

`POST /api/admin/strategy-instances/{strategyInstanceId}/prompt-versions/{promptVersionId}/activate`

요청:

```json
{
  "version": 7
}
```

### 8.12 감시 종목 목록 조회

`GET /api/admin/strategy-instances/{strategyInstanceId}/watchlist`

### 8.13 감시 종목 추가

`POST /api/admin/strategy-instances/{strategyInstanceId}/watchlist`

요청:

```json
{
  "assetMasterId": "uuid"
}
```

### 8.14 감시 종목 제거

`DELETE /api/admin/strategy-instances/{strategyInstanceId}/watchlist/{assetMasterId}`

### 8.15 모델 설정 목록

`GET /api/admin/model-profiles`

쿼리:

- `purpose`: optional
- `enabled`: optional

### 8.16 모델 설정 생성/수정

- `POST /api/admin/model-profiles`
- `PATCH /api/admin/model-profiles/{modelProfileId}`

### 8.17 브로커 계좌 목록

`GET /api/admin/broker-accounts`

### 8.18 브로커 계좌 생성/수정

- `POST /api/admin/broker-accounts`
- `PATCH /api/admin/broker-accounts/{brokerAccountId}`

### 8.19 글로벌 종목 마스터 목록

`GET /api/admin/assets`

쿼리:

- `q`: optional
- `hidden`: optional

### 8.20 글로벌 종목 마스터 생성/수정

- `POST /api/admin/assets`
- `PATCH /api/admin/assets/{assetMasterId}`

비고:

- `dartCorpCode`는 종목 필드로 관리한다.

### 8.21 글로벌 운영 설정 조회/수정

- `GET /api/admin/system-parameters`
- `PATCH /api/admin/system-parameters/{parameterKey}`

## 9. 운영 보조 API

### 9.1 audit log 조회

`GET /api/admin/audit-logs`

- 인증: 필요
- 목적: 별도 UI 연결 여부와 무관하게 운영 추적용 API 제공

쿼리:

- `targetType`: optional
- `targetId`: optional
- `actorType`: optional
- `dateFrom`: optional
- `dateTo`: optional
- `page`, `size`

### 9.2 ops event 조회

`GET /api/admin/ops-events`

- 인증: 필요

쿼리:

- `serviceName`: optional
- `statusCode`: optional
- `strategyInstanceId`: optional
- `dateFrom`: optional
- `dateTo`: optional
- `page`, `size`

비고:

- `ops_event`는 로그성 보조 저장소이므로, 구조 로그와 모니터링을 대체하지 않는다.

## 10. 화면별 API 매핑

### 10.1 대시보드

- `GET /api/dashboard/strategy-overview`
- `GET /api/dashboard/instances/{strategyInstanceId}`
- `GET /api/dashboard/system-status`

### 10.2 매매이력

- `GET /api/trade-orders`
- `GET /api/trade-orders/{tradeOrderId}`
- `GET /api/trade-decisions`
- `GET /api/trade-decisions/{tradeDecisionLogId}`

### 10.3 뉴스·공시

- `GET /api/news`
- `GET /api/disclosures`

### 10.4 차트

- `GET /api/charts/minutes`
- `GET /api/charts/order-overlays`

### 10.5 설정

- `/api/admin/strategy-templates*`
- `/api/admin/strategy-instances*`
- `/api/admin/model-profiles*`
- `/api/admin/broker-accounts*`
- `/api/admin/assets*`
- `/api/admin/system-parameters*`

## 11. 구현 시 주의사항

- 대시보드 30초 자동 갱신 중 `401`은 전체 리다이렉트 대신 자동 갱신 중지 + 인라인 알림으로 처리한다.
- 판단 상세의 `requestText`, `responseText`, `stdoutText`, `stderrText`는 민감정보/저작권 이슈를 고려해 관리자 권한에서만 노출 범위를 더 좁힐 수 있다.
- 설정 저장 API는 변경 즉시 DB에 반영되지만, 현재 진행 중인 사이클에는 영향을 주지 않는다.
- `paper`와 `live`는 같은 주문 상태머신을 공유하지만, `paper`에서는 일반적으로 `trade_order_intent`와 `trade_order`가 1:1에 가깝다.
- 차트 API는 종목별 장중 1분봉 조회를 기본으로 하며, 다중 종목 오버레이 같은 고급 기능은 후속 범위로 둔다.
