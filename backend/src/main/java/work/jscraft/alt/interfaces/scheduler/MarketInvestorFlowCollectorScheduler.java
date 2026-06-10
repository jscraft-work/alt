package work.jscraft.alt.interfaces.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.marketdata.KisInvestorFlowCollector;
import work.jscraft.alt.common.config.ApplicationProfiles;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;

/**
 * 투자자 매매동향(개인/외국인/기관계)은 일 단위 데이터라 일 1회만 수집한다.
 * KST 평일 17:10 (장마감 15:30 + 확정치 여유).
 */
@Component
@Profile(ApplicationProfiles.COLLECTOR_WORKER)
public class MarketInvestorFlowCollectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketInvestorFlowCollectorScheduler.class);

    private final AssetMasterRepository assetMasterRepository;
    private final KisInvestorFlowCollector investorFlowCollector;

    public MarketInvestorFlowCollectorScheduler(
            AssetMasterRepository assetMasterRepository,
            KisInvestorFlowCollector investorFlowCollector) {
        this.assetMasterRepository = assetMasterRepository;
        this.investorFlowCollector = investorFlowCollector;
    }

    @Scheduled(cron = "${app.collector.investor-flow.cron:0 10 17 * * MON-FRI}",
               zone = "Asia/Seoul")
    public void runInvestorFlowCycle() {
        for (String symbolCode : activeSymbolCodes()) {
            try {
                investorFlowCollector.collect(symbolCode);
            } catch (MarketDataException ex) {
                log.warn("investorflow collect failed symbol={} category={} message={}",
                        symbolCode, ex.getCategory(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("investorflow collect unexpected error symbol={} message={}",
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
