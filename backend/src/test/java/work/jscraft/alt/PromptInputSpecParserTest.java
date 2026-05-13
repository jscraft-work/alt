package work.jscraft.alt;

import org.junit.jupiter.api.Test;

import work.jscraft.alt.trading.application.inputspec.PromptInputSpec;
import work.jscraft.alt.trading.application.inputspec.PromptInputSpec.Scope;
import work.jscraft.alt.trading.application.inputspec.PromptInputSpecException;
import work.jscraft.alt.trading.application.inputspec.PromptInputSpecParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptInputSpecParserTest {

    private final PromptInputSpecParser parser = new PromptInputSpecParser();

    @Test
    void parsesAllKeysAndBody() {
        String prompt = """
                ---
                minute_bars: 120
                daily_bars: 30
                news_hours: 12
                disclosure_hours: 24
                trade_history_days: 30
                fundamental: true
                macro: false
                orderbook: false
                scope: full_watchlist
                ---
                <system>You are a trader.</system>
                {% for s in stocks %}
                <stock code="{{ s.code }}">{{ s.minute_bars }}</stock>
                {% endfor %}
                """;

        PromptInputSpec spec = parser.parse(prompt);

        assertThat(spec.minuteBars()).isEqualTo(120);
        assertThat(spec.dailyBars()).isEqualTo(30);
        assertThat(spec.newsHours()).isEqualTo(12);
        assertThat(spec.disclosureHours()).isEqualTo(24);
        assertThat(spec.tradeHistoryDays()).isEqualTo(30);
        assertThat(spec.fundamental()).isTrue();
        assertThat(spec.macro()).isFalse();
        assertThat(spec.orderbook()).isFalse();
        assertThat(spec.scope()).isEqualTo(Scope.FULL_WATCHLIST);
        assertThat(spec.body()).contains("{% for s in stocks %}");
        assertThat(spec.body()).doesNotContain("---");
    }

    @Test
    void omittedKeysBecomeNullOrFalse() {
        String prompt = """
                ---
                minute_bars: 60
                ---
                body
                """;

        PromptInputSpec spec = parser.parse(prompt);

        assertThat(spec.minuteBars()).isEqualTo(60);
        assertThat(spec.dailyBars()).isNull();
        assertThat(spec.newsHours()).isNull();
        assertThat(spec.disclosureHours()).isNull();
        assertThat(spec.tradeHistoryDays()).isNull();
        assertThat(spec.fundamental()).isFalse();
        assertThat(spec.macro()).isFalse();
        assertThat(spec.orderbook()).isFalse();
        assertThat(spec.scope()).isEqualTo(Scope.FULL_WATCHLIST);
    }

    @Test
    void emptyFrontmatterDefaultsAllNullOrFalse() {
        String prompt = "---\n---\nbody";
        PromptInputSpec spec = parser.parse(prompt);
        assertThat(spec.minuteBars()).isNull();
        assertThat(spec.fundamental()).isFalse();
        assertThat(spec.scope()).isEqualTo(Scope.FULL_WATCHLIST);
    }

    @Test
    void rejectsMissingFrontmatter() {
        assertThatThrownBy(() -> parser.parse("plain prompt"))
                .isInstanceOf(PromptInputSpecException.class)
                .hasMessageContaining("frontmatter");
    }

    @Test
    void rejectsMissingClosingDelimiter() {
        assertThatThrownBy(() -> parser.parse("---\nminute_bars: 60\nbody without close"))
                .isInstanceOf(PromptInputSpecException.class)
                .hasMessageContaining("종료");
    }

    @Test
    void rejectsNonIntegerLookback() {
        assertThatThrownBy(() -> parser.parse(
                "---\nminute_bars: hello\n---\nbody"))
                .isInstanceOf(PromptInputSpecException.class)
                .hasMessageContaining("minute_bars");
    }

    @Test
    void rejectsUnknownScope() {
        assertThatThrownBy(() -> parser.parse(
                "---\nscope: nope\n---\nbody"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
