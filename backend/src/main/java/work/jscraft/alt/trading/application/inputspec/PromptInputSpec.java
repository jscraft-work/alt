package work.jscraft.alt.trading.application.inputspec;

import java.util.List;

/**
 * YAML frontmatter로 prompt 상단에 선언되는 입력 스펙.
 *
 * 예:
 * <pre>
 * ---
 * sources:
 *   - {type: minute_bar, lookback_minutes: 120}
 *   - {type: fundamental}
 *   - {type: news, lookback_hours: 12}
 * scope: full_watchlist
 * ---
 * (Pebble 본문...)
 * </pre>
 */
public record PromptInputSpec(
        List<SourceSpec> sources,
        Scope scope,
        String body
) {

    public boolean usesSource(SourceSpec.Type type) {
        return sources.stream().anyMatch(s -> s.type() == type);
    }

    public sealed interface SourceSpec permits SourceSpec.MinuteBar, SourceSpec.Fundamental,
            SourceSpec.News, SourceSpec.Disclosure, SourceSpec.Macro, SourceSpec.Orderbook {

        Type type();

        enum Type {
            MINUTE_BAR("minute_bar"),
            FUNDAMENTAL("fundamental"),
            NEWS("news"),
            DISCLOSURE("disclosure"),
            MACRO("macro"),
            ORDERBOOK("orderbook");

            private final String wireKey;

            Type(String wireKey) { this.wireKey = wireKey; }

            public String wireKey() { return wireKey; }

            public static Type fromWireKey(String key) {
                for (Type t : values()) {
                    if (t.wireKey.equals(key)) return t;
                }
                throw new IllegalArgumentException("알 수 없는 source type: " + key);
            }
        }

        record MinuteBar(int lookbackMinutes) implements SourceSpec {
            @Override public Type type() { return Type.MINUTE_BAR; }
        }

        record Fundamental() implements SourceSpec {
            @Override public Type type() { return Type.FUNDAMENTAL; }
        }

        record News(int lookbackHours) implements SourceSpec {
            @Override public Type type() { return Type.NEWS; }
        }

        record Disclosure(int lookbackHours) implements SourceSpec {
            @Override public Type type() { return Type.DISCLOSURE; }
        }

        record Macro() implements SourceSpec {
            @Override public Type type() { return Type.MACRO; }
        }

        record Orderbook() implements SourceSpec {
            @Override public Type type() { return Type.ORDERBOOK; }
        }
    }

    public enum Scope {
        HELD_ONLY("held_only"),
        FULL_WATCHLIST("full_watchlist");

        private final String wireKey;

        Scope(String wireKey) { this.wireKey = wireKey; }

        public String wireKey() { return wireKey; }

        public static Scope fromWireKey(String key) {
            for (Scope s : values()) {
                if (s.wireKey.equals(key)) return s;
            }
            throw new IllegalArgumentException("알 수 없는 scope: " + key);
        }
    }
}
