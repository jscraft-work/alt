package work.jscraft.alt.collector.application.marketdata;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MarketDailyAggregator {

    public static final String SOURCE_NAME = "aggregated:market_minute_item";

    private static final Logger log = LoggerFactory.getLogger(MarketDailyAggregator.class);

    private static final String UPSERT_SQL = """
            INSERT INTO market_daily_item (
                symbol_code, business_date,
                open_price, high_price, low_price, close_price,
                volume, source_name
            )
            SELECT symbol_code, business_date,
                   (array_agg(open_price ORDER BY bar_time))[1],
                   MAX(high_price), MIN(low_price),
                   (array_agg(close_price ORDER BY bar_time DESC))[1],
                   SUM(volume),
                   ?
              FROM market_minute_item
             WHERE business_date = ?
             GROUP BY symbol_code, business_date
            ON CONFLICT (symbol_code, business_date, source_name) DO UPDATE SET
                open_price = EXCLUDED.open_price,
                high_price = EXCLUDED.high_price,
                low_price = EXCLUDED.low_price,
                close_price = EXCLUDED.close_price,
                volume = EXCLUDED.volume
            """;

    private final JdbcTemplate jdbcTemplate;

    public MarketDailyAggregator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int aggregate(LocalDate businessDate) {
        int rows = jdbcTemplate.update(UPSERT_SQL, SOURCE_NAME, businessDate);
        log.info("daily aggregate businessDate={} affectedRows={}", businessDate, rows);
        return rows;
    }
}
