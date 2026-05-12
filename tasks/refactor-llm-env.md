# Refactor. LLM 실행기 경로/Args/Timeout을 환경변수와 코드로 이관

## 1. 배경

현재 `llm_model_profile` 테이블에는 `command`, `args_json`, `default_timeout_seconds` 컬럼이 있어 운영자가 GUI(`/api/admin/model-profiles`)로 편집하는 모델 카탈로그에 함께 묶여 있다. 그러나 실제 이 세 값의 성격은 다음과 같다.

- **머신 의존**: alt-fast 기준 openclaw 경로는 `/Users/jongsoobae/.nvm/versions/node/v24.14.0/bin/openclaw`, nanobot은 `~/.local/bin/nanobot`. 운영자가 GUI로 바꿀 일이 없다.
- **provider 호출 규약이 고정**: alt-fast `backend/app/shared/llm.py` 기준
  - openclaw: `agent --local --session-id <uuid> -m <prompt> --json`
  - nanobot: `agent --no-markdown -s <uuid> -m <prompt>`
  → DB row마다 다를 이유가 없다.
- **session_id는 호출마다 새로 생성**: 정적 DB args에 박을 수 없다.

따라서 운영 의미가 있는 `purpose / provider / model_name / enabled`는 DB에 남기고, 머신 의존 + 호출 규약 + 기본 timeout은 env 바인딩된 `@ConfigurationProperties("app.llm")`과 코드(adapter)로 옮긴다.

추가로 현재 `LlmSubprocessAdapter`는 generic subprocess 실행기일 뿐이라 alt-fast 기준 stdout 후처리(openclaw JSON unwrap, nanobot `🐈` 접두사 제거)와 갱신된 에러 키워드를 모른다. 이 refactor에서 같이 닫는다.

## 2. 결정 (open question 해소)

- `application.yml`의 openclaw/nanobot 기본값은 **머신 독립적 placeholder**(`/usr/local/bin/...`)로 둔다. 실제 머신 경로는 `.env`로 주입.
- `.env.example`에는 alt-fast 실제 경로를 그대로 적어 둔다.

## 3. 참고 자료

- alt-fast Python 구현: `/Users/jongsoobae/workspace/alt-fast/backend/app/shared/llm.py`
  - openclaw 함수 `ask_llm` (lines 27~78): args 시퀀스, JSON unwrap, auth/error 키워드
  - nanobot 함수 `ask_llm_high` (lines 80~125): args 시퀀스, `🐈` 접두사 제거
  - 단, alt-fast의 3회 재시도(`_MAX_RETRIES`)는 **이 프로젝트에서는 채택하지 않는다**. `docs/07-external-integrations.md` §5.1과 §10.2에 "같은 사이클 안에서 재시도 금지"가 명시되어 있음.
- alt-fast `.env`: `/Users/jongsoobae/workspace/alt-fast/.env` — 환경변수 이름/값 참고용.

## 4. 작업 범위

### 4.1 신규 파일

#### `src/main/java/work/jscraft/alt/trading/application/decision/LlmExecutableProperties.java`

```java
package work.jscraft.alt.trading.application.decision;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.llm")
public class LlmExecutableProperties {

    private final Provider openclaw = new Provider();
    private final Provider nanobot = new Provider();

    public Provider getOpenclaw() { return openclaw; }
    public Provider getNanobot() { return nanobot; }

    public Provider forProvider(String name) {
        return switch (name) {
            case "openclaw" -> openclaw;
            case "nanobot"  -> nanobot;
            default -> throw new IllegalArgumentException("지원하지 않는 LLM provider: " + name);
        };
    }

    public static class Provider {
        private String command;
        private int timeoutSeconds;

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
```

- `AltApplication`은 이미 `@ConfigurationPropertiesScan`을 사용하므로 자동 등록된다.

#### `src/main/resources/db/migration/V5__drop_llm_profile_command_columns.sql`

```sql
ALTER TABLE llm_model_profile
    DROP COLUMN command,
    DROP COLUMN args_json,
    DROP COLUMN default_timeout_seconds;
```

#### `src/test/java/work/jscraft/alt/LlmExecutablePropertiesBindingTest.java`

- `AppPropertiesBindingTest`의 패턴을 그대로 따른다.
- `@SpringBootTest`로 `LlmExecutableProperties` 주입.
- `openclaw.command`, `openclaw.timeoutSeconds`, `nanobot.command`, `nanobot.timeoutSeconds`가 `application.yml` 기본값으로 바인딩되는지 검증.
- `forProvider("openclaw")`, `forProvider("nanobot")`이 올바른 Provider를 반환하는지 검증.
- `forProvider("unknown")`이 `IllegalArgumentException`을 던지는지 검증.

#### `src/test/resources/scripts/fake-openclaw.sh` (LlmSubprocessAdapterTest용)

- 환경변수(예: `FAKE_MODE`)로 모드를 받아 success/timeout/auth_error/empty/non_zero_exit/invalid_output 응답을 만든다.
- 테스트는 `LlmExecutableProperties.openclaw.command`에 이 스크립트의 절대경로를 주입한 뒤 `LlmRequest`를 보내 분류 검증.
- 권장 시그니처: 첫 인자가 `agent`로 시작하면 모든 인자를 무시하고 `FAKE_MODE`에 따라 동작.

### 4.2 수정 파일

#### `src/main/resources/application.yml`

기존 `app:` 블록에 `llm` 하위 추가:

```yaml
app:
  timezone: Asia/Seoul
  runtime:
    role: web-app
  auth:
    jwt-secret: ${AUTH_JWT_SECRET:change-this-jwt-secret-change-this-jwt-secret}
    trusted-proxy-cidrs: ${TRUSTED_PROXY_CIDRS:}
  llm:
    openclaw:
      command: ${LLM_OPENCLAW_COMMAND:/usr/local/bin/openclaw}
      timeout-seconds: ${LLM_OPENCLAW_TIMEOUT_SECONDS:60}
    nanobot:
      command: ${LLM_NANOBOT_COMMAND:/usr/local/bin/nanobot}
      timeout-seconds: ${LLM_NANOBOT_TIMEOUT_SECONDS:120}
```

#### `.env.example`

기존 키 뒤에 추가:

```
LLM_OPENCLAW_COMMAND=/Users/jongsoobae/.nvm/versions/node/v24.14.0/bin/openclaw
LLM_OPENCLAW_TIMEOUT_SECONDS=60
LLM_NANOBOT_COMMAND=/Users/jongsoobae/.local/bin/nanobot
LLM_NANOBOT_TIMEOUT_SECONDS=120
```

#### `src/main/java/work/jscraft/alt/trading/application/decision/LlmRequest.java`

`command`, `args` 필드 제거하고 5필드 record로 축소:

```java
public record LlmRequest(
        UUID sessionId,
        String engineName,
        String modelName,
        String promptText,
        Duration timeout) {
}
```

- `TradeDecisionLogService.saveSuccess/saveFailure`는 `request.command()` / `request.args()`를 사용하지 않으므로 영향 없음 (확인 완료).

#### `src/main/java/work/jscraft/alt/integrations/openclaw/LlmSubprocessAdapter.java`

전면 재작성. 핵심:

- 생성자에서 `LlmExecutableProperties` 주입.
- `requestTradingDecision` 안에서 `engineName` 기준 분기로 argv 조립 → `ProcessBuilder` 실행 → 분류 → 성공 시 stdout 후처리.
- 키워드 갱신 (alt-fast 채택):
  - `AUTH_ERROR_KEYWORDS = ["Token refresh failed", "refresh_token_reused", "401"]`
  - `STDOUT_ERROR_KEYWORDS = ["Error calling", "response failed"]` (신규)
- argv 분기는 package-private 메서드 `buildArgv(LlmRequest)`로 분리해서 순수 함수 테스트 가능하게.
- stdout 후처리도 package-private 메서드 `postProcessStdout(String engineName, String stdout)`로 분리.
- 재시도는 그대로 금지 (분류 결과를 그대로 상위에 전달).

argv 분기 의사코드:

```java
List<String> buildArgv(LlmRequest req) {
    String sid = req.sessionId().toString();
    return switch (req.engineName()) {
        case "openclaw" -> List.of(
                properties.getOpenclaw().getCommand(),
                "agent", "--local",
                "--session-id", sid,
                "-m", req.promptText(),
                "--json");
        case "nanobot" -> List.of(
                properties.getNanobot().getCommand(),
                "agent", "--no-markdown",
                "-s", sid,
                "-m", req.promptText());
        default -> throw new IllegalArgumentException(
                "지원하지 않는 LLM provider: " + req.engineName());
    };
}
```

stdout 후처리:

- `openclaw`: stdout을 JSON으로 parse → `payloads[0].text` 추출. parse 실패 또는 필드 부재 시 raw stdout fallback (alt-fast 정책 동일).
- `nanobot`: stdout이 `🐈`로 시작하면 첫 줄을 떼고 나머지를 반환. 그렇지 않으면 그대로.
- 후처리 결과가 비면 `LlmCallResult.emptyOutput`로 처리.
- 후처리 후 STDOUT_ERROR_KEYWORDS에 매치되면 `LlmCallResult.invalidOutput`로 처리.

분류 우선순위 (현재와 동일):

1. ProcessBuilder 시작 실패 → `invalidOutput`
2. timeout (waitFor false) → `timeout`
3. stderr에 AUTH_ERROR_KEYWORDS → `authError`
4. exitCode ≠ 0 → `nonZeroExit`
5. stdout 후처리 결과가 빈 문자열 → `emptyOutput`
6. stdout 후처리 결과가 STDOUT_ERROR_KEYWORDS 매치 → `invalidOutput`
7. 그 외 → `success`

#### `src/main/java/work/jscraft/alt/trading/application/cycle/CycleExecutionOrchestrator.java`

- 필드와 생성자에 `LlmExecutableProperties llmExecutableProperties` 추가.
- `buildLlmRequest` (현재 lines 213-229) 단순화:

```java
private LlmRequest buildLlmRequest(StrategyInstanceEntity instance, SettingsSnapshot snapshot) {
    LlmModelProfileEntity profile = llmModelProfileRepository.findById(snapshot.tradingModelProfileId())
            .orElseThrow(() -> new IllegalStateException(
                    "tradingModelProfile를 찾을 수 없습니다: " + snapshot.tradingModelProfileId()));
    LlmExecutableProperties.Provider executable =
            llmExecutableProperties.forProvider(profile.getProvider());
    return new LlmRequest(
            UUID.randomUUID(),
            profile.getProvider(),
            profile.getModelName(),
            snapshot.promptText(),
            Duration.ofSeconds(Math.max(1, executable.getTimeoutSeconds())));
}
```

- `profile.getCommand() / getArgsJson() / getDefaultTimeoutSeconds()` 호출 제거.

#### `src/main/java/work/jscraft/alt/llm/infrastructure/persistence/LlmModelProfileEntity.java`

- 필드 제거: `command`, `argsJson`, `defaultTimeoutSeconds` (그리고 각각의 getter/setter, JdbcTypeCode 어노테이션 포함).

#### `src/main/java/work/jscraft/alt/llm/application/LlmModelProfileService.java`

- `ModelProfileMutation` sealed interface에서 `command()`, `argsJson()`, `defaultTimeoutSeconds()` 메서드 제거 (현재 lines 142-146).
- `CreateModelProfileRequest` record에서 동일 3개 필드 제거 (lines 168-170).
- `UpdateModelProfileRequest` record에서 동일 3개 필드 제거 (lines 179-181). `version`은 유지.
- `ModelProfileView` record에서 동일 3개 필드 제거 (lines 151-162).
- `apply()` 메서드에서 `setCommand / setArgsJson / setDefaultTimeoutSeconds` 호출 제거 (lines 104-106).
- `toView()` 매퍼에서 해당 컬럼 매핑 제거 (lines 127-129).
- 더 이상 사용되지 않는 import (`JsonNode` 등) 정리.

#### `src/main/java/work/jscraft/alt/ops/application/AppSeedProperties.java`

- 내부 클래스 `ModelProfile`에서 `command`, `timeoutSeconds` 필드와 getter/setter 제거.
- 유지: `purpose`, `provider`, `modelName`.

#### `src/main/java/work/jscraft/alt/ops/application/SeedDataRunner.java`

- `ensureDefaultModelProfile()` (lines 78-98)에서 다음 제거:
  - `profile.setCommand(config.getCommand())` (line 88)
  - `ObjectNode args = objectMapper.createObjectNode(); args.put("temperature", 0.1); profile.setArgsJson(args)` (lines 89-91)
  - `profile.setDefaultTimeoutSeconds(config.getTimeoutSeconds())` (line 92)
- `ObjectMapper` 의존이 더 이상 필요 없으면 생성자/필드/import에서 제거.

### 4.3 테스트 갱신

#### `LlmRequest` 생성자 호출이 들어 있는 모든 테스트 (시그니처가 7→5 필드로 줄어든 만큼 갱신)

- `src/test/java/work/jscraft/alt/LlmSubprocessAdapterTest.java` — 5곳
- `src/test/java/work/jscraft/alt/TradeDecisionLogPersistenceTest.java` — 2곳
- `src/test/java/work/jscraft/alt/TradeOrderIntentGenerationTest.java` — 1곳
- `src/test/java/work/jscraft/alt/OrderIntentSafetyValidationTest.java` — 1곳

각 호출에서 `command`, `args` 인자를 빼고 `(sessionId, engineName, modelName, promptText, timeout)`로 정리.

#### `LlmSubprocessAdapterTest` 시나리오 재설계

기존: `command=/bin/sh args=["-c", "echo '...'; exit 0; #"]` 식으로 fake LLM 출력을 만들었음. 새 구조에서는 adapter가 argv를 hardcode하므로 직접 `/bin/sh -c`를 줄 수 없다.

두 층으로 나눠 테스트:

- **순수 함수 테스트** (subprocess 미실행, package-private 메서드 직접 호출):
  - `buildArgv("openclaw", ...)` → 정확히 `[bin, "agent", "--local", "--session-id", sid, "-m", prompt, "--json"]`
  - `buildArgv("nanobot", ...)` → 정확히 `[bin, "agent", "--no-markdown", "-s", sid, "-m", prompt]`
  - `buildArgv("unknown", ...)` → IllegalArgumentException
  - `postProcessStdout("openclaw", "{\"payloads\":[{\"text\":\"hi\"}]}")` → `"hi"`
  - `postProcessStdout("openclaw", "not-json")` → `"not-json"` (raw fallback)
  - `postProcessStdout("openclaw", "{\"payloads\":[]}")` → `"{\"payloads\":[]}"` (raw fallback)
  - `postProcessStdout("nanobot", "🐈 nanobot\nhi")` → `"hi"`
  - `postProcessStdout("nanobot", "no prefix")` → `"no prefix"`
- **subprocess 통합 테스트** (`fake-openclaw.sh` 사용):
  - SUCCESS: 스크립트가 valid openclaw JSON 응답 → `LlmCallResult.callStatus()==SUCCESS`, `stdoutText`가 unwrap된 텍스트
  - TIMEOUT: 스크립트가 `sleep 999` → `TIMEOUT`
  - AUTH_ERROR: 스크립트가 stderr로 `Token refresh failed` 출력 후 exit 1 → `AUTH_ERROR`
  - NON_ZERO_EXIT: 스크립트가 정상 stdout + exit 1 → `NON_ZERO_EXIT`
  - EMPTY_OUTPUT: 스크립트가 stdout 비움 → `EMPTY_OUTPUT`
  - INVALID_OUTPUT: 스크립트가 stdout에 `Error calling something` → `INVALID_OUTPUT`

#### `src/test/java/work/jscraft/alt/ModelProfileAdminApiTest.java`

- `CreateModelProfileRequest` 호출에서 `command`(`"openai"`), `argsJson`(`jsonObject("temperature", 0.2)`), `defaultTimeoutSeconds`(`45`) 인자 제거 (lines 33-35).
- `UpdateModelProfileRequest` 호출에서 동일 3개 인자 제거 (lines 83-84).
- 응답 JSON에서 해당 필드를 검증하는 `jsonPath`가 있으면 함께 삭제.

#### `src/test/java/work/jscraft/alt/SeedDataBootstrapTest.java`

- 현재 `purpose=trading_decision`, `enabled=true`만 검증하므로 그대로 통과 예상.
- `AppSeedProperties.ModelProfile`에서 사라진 필드를 참조하는 test fixture/주입 설정이 있으면 함께 정리.

#### `src/test/java/work/jscraft/alt/CoreRepositoryMappingTest.java` 및 기타 영속성 테스트

- 새 V5 마이그레이션 누적 후에도 통과해야 함.
- 만약 `command`/`args_json`/`default_timeout_seconds` 컬럼을 직접 set/assert하는 테스트가 있다면 제거.

#### 기타

- `CoreSchemaFlywayTest`, `TradingSchemaFlywayTest`는 V5까지 누적 적용 후 통과해야 한다. 컬럼이 사라진 뒤에도 마이그레이션 전체가 깨지지 않는 것을 확인.

## 5. 영향 없는 파일 (참고)

- `LlmCallResult.java` — `stdoutText`/`stderrText` 등 그대로 사용.
- `TradeDecisionLogService.java` — `request.command()`/`request.args()` 미사용 확인.
- `ModelProfileAdminController.java` — service 위임만 하므로 시그니처 그대로.
- `LlmNewsAssessmentAdapter` (뉴스 평가 어댑터) — 본구현 자체가 별도 task. 이번 작업에서는 손대지 않는다.

## 6. 검증 절차

순서대로 실행해 각 단계가 통과하는 것을 확인한다.

1. 컴파일 확인:
   ```
   ./gradlew compileJava compileTestJava
   ```
2. 마이그레이션 회귀:
   ```
   ./gradlew test --tests "*CoreSchemaFlywayTest" --tests "*TradingSchemaFlywayTest"
   ```
3. 신규 바인딩 테스트:
   ```
   ./gradlew test --tests "*LlmExecutablePropertiesBindingTest"
   ```
4. 어댑터 재작성 검증:
   ```
   ./gradlew test --tests "*LlmSubprocessAdapterTest"
   ```
5. 사이클 회귀:
   ```
   ./gradlew test --tests "*TradeDecisionLogPersistenceTest" \
                  --tests "*TradeOrderIntentGenerationTest" \
                  --tests "*OrderIntentSafetyValidationTest" \
                  --tests "*PaperTradingCycleE2ETest"
   ```
6. 운영 CRUD 회귀:
   ```
   ./gradlew test --tests "*ModelProfileAdminApiTest" --tests "*AdminOptimisticLockApiTest"
   ```
7. 시드 회귀:
   ```
   ./gradlew test --tests "*SeedDataBootstrapTest"
   ```
8. 전체 회귀 (최종):
   ```
   ./gradlew test
   ```
   기존 188개 테스트가 그대로 통과하고, 추가된 신규 테스트(LlmExecutablePropertiesBindingTest + LlmSubprocessAdapterTest 신규 케이스)가 통과해야 한다.

## 7. Out of scope

이번 refactor에서 손대지 않는다 (별도 task로 분리).

- KIS REST 및 WebSocket 어댑터 본구현
- DART / 네이버 / yfinance 어댑터 본구현
- Telegram HTTP 발송 본구현
- `LlmNewsAssessmentAdapter` 본구현 (이번 provider-분기 패턴은 그대로 재사용 가능)
- GitHub Actions 배포 / Secrets 주입 자동화

## 8. 완료 정의

- 위 6번 검증 절차가 모두 통과한다.
- `.env.example`에 새 키 4개가 추가되어 있다.
- `application.yml`에 `app.llm` 블록이 있다.
- `llm_model_profile` 테이블에서 컬럼 3개가 사라져 있다 (Flyway 누적 적용 후 `psql \d llm_model_profile`로 확인 가능).
- 운영자 화면(`/api/admin/model-profiles`)의 요청/응답 페이로드에서 해당 3필드가 빠져 있다.
- `LlmRequest` 레코드가 5필드로 줄어 있다.
- `LlmSubprocessAdapter`가 provider 분기로 argv를 만들고 stdout 후처리를 수행한다.
