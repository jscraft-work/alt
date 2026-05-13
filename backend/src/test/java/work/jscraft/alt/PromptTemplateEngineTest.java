package work.jscraft.alt;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import work.jscraft.alt.trading.application.inputspec.PromptTemplateEngine;
import work.jscraft.alt.trading.application.inputspec.StockContext;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateEngineTest {

    private PromptTemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PromptTemplateEngine();
        engine.init();
    }

    @Test
    void rendersSystemVariables() {
        String body = "now={{ current_time }} cash={{ cash_amount }}";
        Map<String, Object> ctx = Map.of(
                "current_time", "2026-05-13T14:30:00+09:00",
                "cash_amount", "9,824,500 KRW");

        String out = engine.render(body, ctx);

        assertThat(out).isEqualTo("now=2026-05-13T14:30:00+09:00 cash=9,824,500 KRW");
    }

    @Test
    void rendersStocksLoop() {
        String body = """
                {% for s in stocks -%}
                <stock code="{{ s.code }}" name="{{ s.name }}">
                  <minute_bars>{{ s.minute_bars }}</minute_bars>
                  <fundamental>{{ s.fundamental }}</fundamental>
                </stock>
                {% endfor %}""";
        Map<String, Object> ctx = Map.of(
                "stocks", List.of(
                        StockContext.of("005930", "삼성전자",
                                "[14:25] 75100 vol=1200", "", "PER=11.2", "", "", "", ""),
                        StockContext.of("035720", "카카오",
                                "[14:25] 42100 vol=830", "", "PER=N/A", "", "", "", "")));

        String out = engine.render(body, ctx);

        assertThat(out).contains("<stock code=\"005930\" name=\"삼성전자\">");
        assertThat(out).contains("<minute_bars>[14:25] 75100 vol=1200</minute_bars>");
        assertThat(out).contains("<fundamental>PER=11.2</fundamental>");
        assertThat(out).contains("<stock code=\"035720\" name=\"카카오\">");
    }

    @Test
    void missingVariableRendersAsEmpty() {
        String body = "x=[{{ x }}] y=[{{ y }}]";
        Map<String, Object> ctx = Map.of("x", "1");

        String out = engine.render(body, ctx);

        assertThat(out).isEqualTo("x=[1] y=[]");
    }
}
