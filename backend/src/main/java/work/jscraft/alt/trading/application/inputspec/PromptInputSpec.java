package work.jscraft.alt.trading.application.inputspec;

/**
 * Prompt 상단의 YAML frontmatter를 표현하는 record.
 *
 * 예:
 * <pre>
 * ---
 * minute_bars: 60          # 최근 60분 분봉 (null/생략이면 보내지 않음)
 * daily_bars: 30           # 최근 30일 일봉
 * news_hours: 24           # 최근 24시간 뉴스
 * disclosure_hours: 24
 * trade_history_days: 30   # 이 인스턴스 + 종목의 최근 30일 매매이력
 * fundamental: true        # 펀더멘털 1행 첨부
 * macro: false
 * orderbook: false
 * scope: full_watchlist    # held_only | full_watchlist (기본)
 * ---
 * (Pebble 본문...)
 * </pre>
 */
public record PromptInputSpec(
        Integer minuteBars,
        Integer dailyBars,
        Integer newsHours,
        Integer disclosureHours,
        Integer tradeHistoryDays,
        boolean fundamental,
        boolean macro,
        boolean orderbook,
        Scope scope,
        String body) {

    public enum Scope {
        HELD_ONLY("held_only"),
        FULL_WATCHLIST("full_watchlist");

        private final String wireKey;

        Scope(String wireKey) {
            this.wireKey = wireKey;
        }

        public String wireKey() {
            return wireKey;
        }

        public static Scope fromWireKey(String key) {
            for (Scope s : values()) {
                if (s.wireKey.equals(key)) return s;
            }
            throw new IllegalArgumentException("알 수 없는 scope: " + key);
        }
    }
}
