package work.jscraft.alt;

import org.junit.jupiter.api.Test;

import work.jscraft.alt.trading.application.inputspec.PromptInputSpec;
import work.jscraft.alt.trading.application.inputspec.PromptInputSpec.Scope;
import work.jscraft.alt.trading.application.inputspec.PromptInputSpec.SourceSpec;
import work.jscraft.alt.trading.application.inputspec.PromptInputSpecException;
import work.jscraft.alt.trading.application.inputspec.PromptInputSpecParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptInputSpecParserTest {

    private final PromptInputSpecParser parser = new PromptInputSpecParser();

    @Test
    void parsesSourcesAndScopeAndBody() {
        String prompt = """
                ---
                sources:
                  - {type: minute_bar, lookback_minutes: 120}
                  - {type: fundamental}
                  - {type: news, lookback_hours: 12}
                scope: full_watchlist
                ---
                <system>You are a trader.</system>
                {% for s in stocks %}
                <stock code="{{ s.code }}">{{ s.minute_bars }}</stock>
                {% endfor %}
                """;

        PromptInputSpec spec = parser.parse(prompt);

        assertThat(spec.scope()).isEqualTo(Scope.FULL_WATCHLIST);
        assertThat(spec.sources()).hasSize(3);
        assertThat(spec.sources().get(0)).isInstanceOf(SourceSpec.MinuteBar.class);
        assertThat(((SourceSpec.MinuteBar) spec.sources().get(0)).lookbackMinutes()).isEqualTo(120);
        assertThat(spec.sources().get(1)).isInstanceOf(SourceSpec.Fundamental.class);
        assertThat(((SourceSpec.News) spec.sources().get(2)).lookbackHours()).isEqualTo(12);
        assertThat(spec.body()).contains("{% for s in stocks %}");
        assertThat(spec.body()).doesNotContain("---");
    }

    @Test
    void defaultsLookbackWhenOmitted() {
        String prompt = """
                ---
                sources:
                  - {type: minute_bar}
                  - {type: news}
                  - {type: disclosure}
                ---
                body
                """;

        PromptInputSpec spec = parser.parse(prompt);

        assertThat(((SourceSpec.MinuteBar) spec.sources().get(0)).lookbackMinutes()).isEqualTo(60);
        assertThat(((SourceSpec.News) spec.sources().get(1)).lookbackHours()).isEqualTo(12);
        assertThat(((SourceSpec.Disclosure) spec.sources().get(2)).lookbackHours()).isEqualTo(24);
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
        assertThatThrownBy(() -> parser.parse("---\nsources: []\nbody without close"))
                .isInstanceOf(PromptInputSpecException.class)
                .hasMessageContaining("종료");
    }

    @Test
    void rejectsEmptySources() {
        assertThatThrownBy(() -> parser.parse("---\nsources: []\n---\nbody"))
                .isInstanceOf(PromptInputSpecException.class);
    }

    @Test
    void rejectsUnknownSourceType() {
        assertThatThrownBy(() -> parser.parse(
                "---\nsources:\n  - {type: foobar}\n---\nbody"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
