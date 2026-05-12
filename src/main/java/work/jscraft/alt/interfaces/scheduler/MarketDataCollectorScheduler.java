package work.jscraft.alt.interfaces.scheduler;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.marketdata.KisMinuteBarCollector;
import work.jscraft.alt.collector.application.marketdata.KisPriceCollector;
import work.jscraft.alt.common.config.ApplicationProfiles;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;

@Component
@Profile(ApplicationProfiles.COLLECTOR_WORKER)
public class MarketDataCollectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataCollectorScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AssetMasterRepository assetMasterRepository;
    private final KisPriceCollector priceCollector;
    private final KisMinuteBarCollector minuteBarCollector;
    private final Clock clock;

    public MarketDataCollectorScheduler(
            AssetMasterRepository assetMasterRepository,
            KisPriceCollector priceCollector,
            KisMinuteBarCollector minuteBarCollector,
            Clock clock) {
        this.assetMasterRepository = assetMasterRepository;
        this.priceCollector = priceCollector;
        this.minuteBarCollector = minuteBarCollector;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${app.collector.marketdata.initial-delay-ms:60000}",
            fixedDelayString = "${app.collector.marketdata.price-interval-ms:10000}")
    public void runPriceCycle() {
        for (String symbolCode : activeSymbolCodes()) {
            try {
                priceCollector.collect(symbolCode);
            } catch (MarketDataException ex) {
                log.warn("price collect failed symbol={} category={} message={}",
                        symbolCode, ex.getCategory(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("price collect unexpected error symbol={} message={}", symbolCode, ex.getMessage());
            }
        }
    }

    @Scheduled(
            initialDelayString = "${app.collector.marketdata.initial-delay-ms:60000}",
            fixedDelayString = "${app.collector.marketdata.minute-bar-interval-ms:60000}")
    public void runMinuteBarCycle() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        for (String symbolCode : activeSymbolCodes()) {
            try {
                minuteBarCollector.collect(symbolCode, today);
            } catch (MarketDataException ex) {
                log.warn("minute bar collect failed symbol={} category={} message={}",
                        symbolCode, ex.getCategory(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("minute bar collect unexpected error symbol={} message={}", symbolCode, ex.getMessage());
            }
        }
    }

    private List<String> activeSymbolCodes() {
        return assetMasterRepository.findAll().stream()
                .filter(asset -> !asset.isHidden())
                .map(AssetMasterEntity::getSymbolCode)
                .toList();
    }
}
