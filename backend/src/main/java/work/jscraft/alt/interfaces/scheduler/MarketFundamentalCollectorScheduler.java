package work.jscraft.alt.interfaces.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.marketdata.KisFundamentalCollector;
import work.jscraft.alt.common.config.ApplicationProfiles;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;

/**
 * 펀더멘털은 변동성이 낮아 일 1회만 수집한다. KST 평일 17:00 (장마감 15:30 + 여유).
 */
@Component
@Profile(ApplicationProfiles.COLLECTOR_WORKER)
public class MarketFundamentalCollectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketFundamentalCollectorScheduler.class);

    private final AssetMasterRepository assetMasterRepository;
    private final KisFundamentalCollector fundamentalCollector;

    public MarketFundamentalCollectorScheduler(
            AssetMasterRepository assetMasterRepository,
            KisFundamentalCollector fundamentalCollector) {
        this.assetMasterRepository = assetMasterRepository;
        this.fundamentalCollector = fundamentalCollector;
    }

    @Scheduled(cron = "${app.collector.fundamental.cron:0 0 17 * * MON-FRI}",
               zone = "Asia/Seoul")
    public void runFundamentalCycle() {
        for (String symbolCode : activeSymbolCodes()) {
            try {
                fundamentalCollector.collect(symbolCode);
            } catch (MarketDataException ex) {
                log.warn("fundamental collect failed symbol={} category={} message={}",
                        symbolCode, ex.getCategory(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("fundamental collect unexpected error symbol={} message={}",
                        symbolCode, ex.getMessage());
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
