package work.jscraft.alt.trading.application.inputspec;

import java.util.Arrays;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * prompt 상단의 YAML frontmatter (--- ... ---)를 파싱해 {@link PromptInputSpec}으로 변환.
 * frontmatter가 없으면 {@link PromptInputSpecException} 던짐.
 */
@Component
public class PromptInputSpecParser {

    private static final String DELIMITER = "---";

    public PromptInputSpec parse(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new PromptInputSpecException("prompt가 비어 있습니다.");
        }
        String[] lines = prompt.split("\\R", -1);
        if (lines.length < 2 || !lines[0].trim().equals(DELIMITER)) {
            throw new PromptInputSpecException(
                    "prompt 상단에 YAML frontmatter('---'로 시작/끝)가 없습니다.");
        }
        int closeIdx = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().equals(DELIMITER)) {
                closeIdx = i;
                break;
            }
        }
        if (closeIdx < 0) {
            throw new PromptInputSpecException("YAML frontmatter 종료 '---'를 찾을 수 없습니다.");
        }

        String yamlBlock = String.join("\n", Arrays.copyOfRange(lines, 1, closeIdx));
        String body = String.join("\n", Arrays.copyOfRange(lines, closeIdx + 1, lines.length));

        Object raw = new Yaml().load(yamlBlock);
        if (raw == null) {
            // 빈 frontmatter 허용 (모두 기본값)
            return new PromptInputSpec(null, null, null, null, null,
                    false, false, false, PromptInputSpec.Scope.FULL_WATCHLIST, body);
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new PromptInputSpecException("frontmatter는 YAML 객체여야 합니다.");
        }

        Integer minuteBars = optInt(map, "minute_bars");
        Integer dailyBars = optInt(map, "daily_bars");
        Integer newsHours = optInt(map, "news_hours");
        Integer disclosureHours = optInt(map, "disclosure_hours");
        Integer tradeHistoryDays = optInt(map, "trade_history_days");
        boolean fundamental = optBool(map, "fundamental");
        boolean macro = optBool(map, "macro");
        boolean orderbook = optBool(map, "orderbook");
        PromptInputSpec.Scope scope = parseScope(map.get("scope"));

        return new PromptInputSpec(minuteBars, dailyBars, newsHours, disclosureHours,
                tradeHistoryDays, fundamental, macro, orderbook, scope, body);
    }

    private static Integer optInt(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return l.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new PromptInputSpecException(key + "는 정수여야 합니다: " + s);
            }
        }
        throw new PromptInputSpecException(key + "는 정수여야 합니다: " + v);
    }

    private static boolean optBool(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            if ("true".equalsIgnoreCase(s)) return true;
            if ("false".equalsIgnoreCase(s)) return false;
        }
        throw new PromptInputSpecException(key + "는 boolean이어야 합니다: " + v);
    }

    private static PromptInputSpec.Scope parseScope(Object raw) {
        if (raw == null) return PromptInputSpec.Scope.FULL_WATCHLIST;
        if (!(raw instanceof String s)) {
            throw new PromptInputSpecException("'scope'는 문자열이어야 합니다.");
        }
        return PromptInputSpec.Scope.fromWireKey(s);
    }
}
