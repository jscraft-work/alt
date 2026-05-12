package work.jscraft.alt.collector.application.marketdata;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.application.MarketDataGateway;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.MinuteBar;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketMinuteItemRepository;

@Component
public class KisMinuteBarCollector {

    public static final String EVENT_TYPE = "marketdata.minute-bar.collect";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MarketDataGateway marketDataGateway;
    private final MarketMinuteItemRepository marketMinuteItemRepository;
    private final MarketDataOpsEventRecorder opsEventRecorder;

    public KisMinuteBarCollector(
            MarketDataGateway marketDataGateway,
            MarketMinuteItemRepository marketMinuteItemRepository,
            MarketDataOpsEventRecorder opsEventRecorder) {
        this.marketDataGateway = marketDataGateway;
        this.marketMinuteItemRepository = marketMinuteItemRepository;
        this.opsEventRecorder = opsEventRecorder;
    }

    public CollectResult collect(String symbolCode, LocalDate businessDate) {
        try {
            List<MinuteBar> bars = marketDataGateway.fetchMinuteBars(symbolCode, businessDate);
            int saved = 0;
            int skipped = 0;
            for (MinuteBar bar : bars) {
                boolean exists = marketMinuteItemRepository
                        .existsBySymbolCodeAndBarTimeAndSourceName(bar.symbolCode(), bar.barTime(), bar.sourceName());
                if (exists) {
                    skipped++;
                    continue;
                }
                MarketMinuteItemEntity entity = new MarketMinuteItemEntity();
                entity.setSymbolCode(bar.symbolCode());
                entity.setBarTime(bar.barTime());
                entity.setBusinessDate(bar.barTime().atZoneSameInstant(KST).toLocalDate());
                entity.setOpenPrice(bar.openPrice());
                entity.setHighPrice(bar.highPrice());
                entity.setLowPrice(bar.lowPrice());
                entity.setClosePrice(bar.closePrice());
                entity.setVolume(bar.volume());
                entity.setSourceName(bar.sourceName());
                marketMinuteItemRepository.saveAndFlush(entity);
                saved++;
            }
            opsEventRecorder.recordSuccess(
                    MarketDataOpsEventRecorder.SERVICE_MARKETDATA,
                    EVENT_TYPE,
                    symbolCode);
            return new CollectResult(saved, skipped);
        } catch (MarketDataException ex) {
            opsEventRecorder.recordFailure(
                    MarketDataOpsEventRecorder.SERVICE_MARKETDATA,
                    EVENT_TYPE,
                    symbolCode,
                    ex.getMessage(),
                    ex.getCategory().name());
            throw ex;
        }
    }

    public record CollectResult(int saved, int skipped) {
    }
}
