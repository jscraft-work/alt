# alt-java 배포 안내

자세한 인프라 그림은 [jscraft-infra/docs/deployment.md](../jscraft-infra/docs/deployment.md)에 있다. 여기서는 alt-java 운영자가 알아야 할 부분만 모은다.

## 1. 흐름 한 줄

```
git push main
   │
   ▼
.github/workflows/ci.yml (Ubuntu)
   ├─ test            (./gradlew test)
   ├─ build-and-push  (ARM Docker image → ghcr.io/jscraft-work/alt:<sha>)
   └─ deploy
        ├─ POST https://deploy.jscraft.work/webhook/env-sync   target=alt, env=<GitHub Secrets>
        └─ POST https://deploy.jscraft.work/webhook/deploy     image=ghcr.io/.../alt:<sha>
                    │
                    ▼
        맥미니의 Hono deploy 서버
          ├─ /opt/jscraft/apps/alt/.env 갱신
          ├─ docker compose pull
          └─ docker compose up -d        (3 컨테이너 동시 재기동)
```

3 컨테이너 = `alt-web-app`, `alt-trading-worker`, `alt-collector-worker`. 모두 같은 이미지를 쓰고 `LOADER_MAIN` 환경변수로 진입 클래스를 분기한다 (`backend/Dockerfile` 참조).

## 2. 외부 의존

| 컴포넌트 | 위치 | 용도 |
| --- | --- | --- |
| PostgreSQL `alt` DB | `jscraft` Docker 네트워크의 `postgres:5432` | 운영 데이터 |
| Redis | 같은 네트워크의 `redis:6379` | 세션/락/장중 호가 캐시 |
| LLM HTTP wrapper | 맥미니 호스트 launchd, `127.0.0.1:18000` | `POST /ask`로 openclaw/nanobot subprocess 위임 |
| Cloudflare 터널 | 외부 → 맥미니 nginx | `alt.jscraft.work` 경유 노출 |

컨테이너 → 호스트 wrapper 도달은 `host.docker.internal:18000` (`apps/alt/docker-compose.yml`에 `extra_hosts: host-gateway` 명시).

## 3. 환경변수

소스: `jscraft-infra/apps/alt/.env.example` (운영 값은 GitHub Secrets로 주입).

핵심 키만:

```
DB_URL=jdbc:postgresql://postgres:5432/alt
DB_USERNAME=...
DB_PASSWORD=...
REDIS_HOST=redis
REDIS_PORT=6379
AUTH_JWT_SECRET=...        # 32자 이상
LLM_HTTP_BASE_URL=http://host.docker.internal:18000
LLM_OPENCLAW_TIMEOUT_SECONDS=60
LLM_NANOBOT_TIMEOUT_SECONDS=120
KIS_APP_KEY=... / KIS_APP_SECRET=... / KIS_ENVIRONMENT=real
DART_API_KEY=...
NAVER_CLIENT_ID=... / NAVER_CLIENT_SECRET=...
TELEGRAM_BOT_TOKEN=... / TELEGRAM_CHAT_ID=...
```

같은 키 이름으로 GitHub repository secrets 또는 organization secrets에 등록되어 있으면 `env-sync` webhook이 자동으로 `.env`에 채워 준다.

## 4. 운영자가 한 번 손으로 해야 하는 일

운영 PostgreSQL은 이미 PGDATA가 차 있어서 `jscraft-infra/postgres/init.sql`이 자동 실행되지 않는다. 그래서 alt DB는 **운영자가 한 번 만들어야 한다**:

```bash
docker compose -f /opt/jscraft/jscraft-infra/infra/docker-compose.yml \
  exec postgres psql -U "$DB_USERNAME" \
  -tc "SELECT 1 FROM pg_database WHERE datname='alt'" | grep -q 1 \
  || docker compose -f /opt/jscraft/jscraft-infra/infra/docker-compose.yml \
       exec postgres psql -U "$DB_USERNAME" -c "CREATE DATABASE alt;"
```

이후 첫 배포가 끝나면 `web-app` 컨테이너 부팅 시 Flyway가 V1~V6 마이그레이션을 자동 적용한다 (`FLYWAY_ENABLED=true`).

## 5. 첫 배포 후 확인 명령

```bash
# 컨테이너 상태
docker compose -f /opt/jscraft/jscraft-infra/apps/alt/docker-compose.yml ps

# health
curl -sf http://localhost:8081/actuator/health/readiness

# 스케줄러 상태 (db-scheduler 잡 목록)
docker compose ... exec postgres psql -U "$DB_USERNAME" -d alt \
  -c "SELECT task_name, task_instance, execution_time, picked FROM scheduled_tasks;"

# LLM wrapper 도달
docker compose ... exec alt-trading-worker \
  curl -sf http://host.docker.internal:18000/health || echo "wrapper unreachable"
```

## 6. 사이클 운영자 시점 시나리오

1. 운영자 로그인 → 전략 템플릿 생성 → 전략 인스턴스 생성(draft, paper)
2. 감시 종목 등록 → 프롬프트/예산 채움 → `lifecycle:active` 토글
3. 1분 안에 trading-worker의 `TradingCycleReconciler`가 `scheduled_tasks`에 row 등록
4. cycleMinutes 주기로 사이클 실행 → `trade_cycle_log`/`trade_decision_log`/`trade_order` 생성
5. 실패 시 Telegram 알림 + `ops_event` row 기록

자세한 사이클 단계는 [docs/05-trading-cycle-design.md](docs/05-trading-cycle-design.md) 참조.

## 7. 장애 대응 짧게

| 증상 | 1차 확인 |
| --- | --- |
| 사이클이 안 돔 | `SELECT * FROM scheduled_tasks` — row가 있나 / `lifecycle_state='active'`인지 / `auto_paused_reason` 확인 |
| LLM 실패 알림 폭주 | `curl http://host.docker.internal:18000/ask`로 wrapper 직접 확인. launchd 상태 점검 |
| live 인스턴스가 자동 일시중지 | `auto_paused_reason='reconcile_failed'` — KIS API 또는 broker 계좌 상태 확인. 운영자가 lifecycle을 inactive→active로 재토글하면 reconciler가 다시 등록 |
| Flyway 충돌 | `flyway_schema_history` 테이블에서 `success=false` row 확인. 실패 마이그레이션은 운영자가 직접 정리 후 web-app 재기동 |
