package work.jscraft.alt.interfaces.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.marketdata.MarketDailyAggregator;
import work.jscraft.alt.common.config.ApplicationProfiles;

/**
 * 장마감(KST 15:30) 후 분봉을 일봉으로 집계해 market_daily_item 에 upsert.
 */
@Component
@Profile(ApplicationProfiles.COLLECTOR_WORKER)
public class MarketDailyAggregatorScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(MarketDailyAggregatorScheduler.class);

    private final MarketDailyAggregator aggregator;

    public MarketDailyAggregatorScheduler(MarketDailyAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Scheduled(cron = "${app.collector.daily-aggregate.cron:0 0 16 * * MON-FRI}",
               zone = "Asia/Seoul")
    public void runDailyAggregate() {
        LocalDate today = LocalDate.now(KST);
        try {
            aggregator.aggregate(today);
        } catch (RuntimeException ex) {
            log.warn("daily aggregate failed date={} message={}", today, ex.getMessage());
        }
    }
}
