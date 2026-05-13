package work.jscraft.alt.trading.application.inputspec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * prompt 상단의 YAML frontmatter (--- ... ---)를 파싱해 {@link PromptInputSpec}으로 변환.
 * frontmatter가 없으면 {@link PromptInputSpecException} 던짐 (활성화 게이트에서 사용).
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

        String yamlBlock = String.join("\n", java.util.Arrays.copyOfRange(lines, 1, closeIdx));
        String body = String.join("\n", java.util.Arrays.copyOfRange(lines, closeIdx + 1, lines.length));

        Object raw = new Yaml().load(yamlBlock);
        if (!(raw instanceof Map<?, ?> map)) {
            throw new PromptInputSpecException("frontmatter는 YAML 객체여야 합니다.");
        }

        List<PromptInputSpec.SourceSpec> sources = parseSources(map.get("sources"));
        PromptInputSpec.Scope scope = parseScope(map.get("scope"));

        return new PromptInputSpec(sources, scope, body);
    }

    @SuppressWarnings("unchecked")
    private List<PromptInputSpec.SourceSpec> parseSources(Object raw) {
        if (raw == null) {
            throw new PromptInputSpecException("frontmatter에 'sources'가 없습니다.");
        }
        if (!(raw instanceof List<?> list)) {
            throw new PromptInputSpecException("'sources'는 리스트여야 합니다.");
        }
        List<PromptInputSpec.SourceSpec> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                throw new PromptInputSpecException("'sources' 항목은 객체여야 합니다: " + item);
            }
            Map<String, Object> m = (Map<String, Object>) item;
            Object typeRaw = m.get("type");
            if (!(typeRaw instanceof String typeKey)) {
                throw new PromptInputSpecException("'sources[].type'은 문자열이어야 합니다.");
            }
            out.add(buildSource(PromptInputSpec.SourceSpec.Type.fromWireKey(typeKey), m));
        }
        if (out.isEmpty()) {
            throw new PromptInputSpecException("'sources'가 비어 있습니다.");
        }
        return out;
    }

    private PromptInputSpec.SourceSpec buildSource(
            PromptInputSpec.SourceSpec.Type type, Map<String, Object> m) {
        return switch (type) {
            case MINUTE_BAR -> new PromptInputSpec.SourceSpec.MinuteBar(
                    intParam(m, "lookback_minutes", 60));
            case FUNDAMENTAL -> new PromptInputSpec.SourceSpec.Fundamental();
            case NEWS -> new PromptInputSpec.SourceSpec.News(
                    intParam(m, "lookback_hours", 12));
            case DISCLOSURE -> new PromptInputSpec.SourceSpec.Disclosure(
                    intParam(m, "lookback_hours", 24));
            case MACRO -> new PromptInputSpec.SourceSpec.Macro();
            case ORDERBOOK -> new PromptInputSpec.SourceSpec.Orderbook();
        };
    }

    private int intParam(Map<String, Object> m, String key, int defaultValue) {
        Object v = m.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return l.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) {
                throw new PromptInputSpecException(key + "는 정수여야 합니다: " + s);
            }
        }
        throw new PromptInputSpecException(key + "는 정수여야 합니다: " + v);
    }

    private PromptInputSpec.Scope parseScope(Object raw) {
        if (raw == null) {
            return PromptInputSpec.Scope.FULL_WATCHLIST;
        }
        if (!(raw instanceof String s)) {
            throw new PromptInputSpecException("'scope'는 문자열이어야 합니다.");
        }
        return PromptInputSpec.Scope.fromWireKey(s);
    }
}
