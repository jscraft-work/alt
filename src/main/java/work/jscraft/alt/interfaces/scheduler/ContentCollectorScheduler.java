package work.jscraft.alt.interfaces.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.disclosure.DartDisclosureCollector;
import work.jscraft.alt.collector.application.macro.MacroCollector;
import work.jscraft.alt.collector.application.news.NaverNewsCollector;
import work.jscraft.alt.collector.application.news.NewsAssessmentWorkflow;
import work.jscraft.alt.common.config.ApplicationProfiles;
import work.jscraft.alt.disclosure.application.DisclosureGatewayException;
import work.jscraft.alt.macro.application.MacroGatewayException;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterRepository;
import work.jscraft.alt.news.application.NewsGatewayException;

@Component
@Profile(ApplicationProfiles.COLLECTOR_WORKER)
public class ContentCollectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContentCollectorScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration NEWS_LOOKBACK = Duration.ofHours(6);
    private static final Duration DISCLOSURE_LOOKBACK = Duration.ofHours(12);

    private final AssetMasterRepository assetMasterRepository;
    private final NaverNewsCollector naverNewsCollector;
    private final NewsAssessmentWorkflow newsAssessmentWorkflow;
    private final DartDisclosureCollector dartDisclosureCollector;
    private final MacroCollector macroCollector;
    private final Clock clock;

    public ContentCollectorScheduler(
            AssetMasterRepository assetMasterRepository,
            NaverNewsCollector naverNewsCollector,
            NewsAssessmentWorkflow newsAssessmentWorkflow,
            DartDisclosureCollector dartDisclosureCollector,
            MacroCollector macroCollector,
            Clock clock) {
        this.assetMasterRepository = assetMasterRepository;
        this.naverNewsCollector = naverNewsCollector;
        this.newsAssessmentWorkflow = newsAssessmentWorkflow;
        this.dartDisclosureCollector = dartDisclosureCollector;
        this.macroCollector = macroCollector;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${app.collector.content.initial-delay-ms:60000}",
            fixedDelayString = "${app.collector.content.news-interval-ms:300000}")
    public void runNewsCycle() {
        OffsetDateTime since = OffsetDateTime.now(clock).minus(NEWS_LOOKBACK);
        for (String symbolCode : activeSymbolCodes()) {
            try {
                naverNewsCollector.collect(symbolCode, since);
            } catch (NewsGatewayException ex) {
                log.warn("news collect failed symbol={} category={} message={}",
                        symbolCode, ex.getCategory(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("news collect unexpected error symbol={} message={}", symbolCode, ex.getMessage());
            }
        }
        try {
            newsAssessmentWorkflow.assessUnclassified();
        } catch (RuntimeException ex) {
            log.warn("news assessment failed message={}", ex.getMessage());
        }
    }

    @Scheduled(
            initialDelayString = "${app.collector.content.initial-delay-ms:60000}",
            fixedDelayString = "${app.collector.content.disclosure-interval-ms:600000}")
    public void runDisclosureCycle() {
        OffsetDateTime since = OffsetDateTime.now(clock).minus(DISCLOSURE_LOOKBACK);
        for (AssetMasterEntity asset : assetMasterRepository.findAll()) {
            if (asset.isHidden() || asset.getDartCorpCode() == null) {
                continue;
            }
            try {
                dartDisclosureCollector.collect(asset.getDartCorpCode(), since);
            } catch (DisclosureGatewayException ex) {
                log.warn("disclosure collect failed dartCorpCode={} category={} message={}",
                        asset.getDartCorpCode(), ex.getCategory(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("disclosure collect unexpected error dartCorpCode={} message={}",
                        asset.getDartCorpCode(), ex.getMessage());
            }
        }
    }

    @Scheduled(
            initialDelayString = "${app.collector.content.initial-delay-ms:60000}",
            fixedDelayString = "${app.collector.content.macro-interval-ms:3600000}")
    public void runMacroCycle() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        try {
            macroCollector.collect(today);
        } catch (MacroGatewayException ex) {
            log.warn("macro collect failed date={} category={} message={}",
                    today, ex.getCategory(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("macro collect unexpected error date={} message={}", today, ex.getMessage());
        }
    }

    private List<String> activeSymbolCodes() {
        return assetMasterRepository.findAll().stream()
                .filter(asset -> !asset.isHidden())
                .map(AssetMasterEntity::getSymbolCode)
                .toList();
    }
}
