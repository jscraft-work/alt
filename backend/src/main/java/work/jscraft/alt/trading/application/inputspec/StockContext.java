package work.jscraft.alt.trading.application.inputspec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prompt 안의 {% for s in stocks %} loop에서 LLM에 노출되는 종목 단위 컨텍스트.
 * Pebble은 Map 키를 자동 접근하므로 변수명을 snake_case로 유지한다 ({{ s.minute_bars }}).
 * frontmatter에 해당 키가 빠져 있으면 빈 문자열로 둔다.
 */
public final class StockContext {

    private StockContext() {}

    public static Map<String, Object> of(
            String code, String name,
            String minuteBars, String dailyBars, String fundamental,
            String news, String disclosures, String orderbook,
            String tradeHistory) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name == null ? "" : name);
        m.put("minute_bars", minuteBars == null ? "" : minuteBars);
        m.put("daily_bars", dailyBars == null ? "" : dailyBars);
        m.put("fundamental", fundamental == null ? "" : fundamental);
        m.put("news", news == null ? "" : news);
        m.put("disclosures", disclosures == null ? "" : disclosures);
        m.put("orderbook", orderbook == null ? "" : orderbook);
        m.put("trade_history", tradeHistory == null ? "" : tradeHistory);
        return m;
    }
}
