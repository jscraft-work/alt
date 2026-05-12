package work.jscraft.alt.collector.application.marketdata;

import java.time.ZoneId;

import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.application.MarketDataGateway;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.PriceSnapshot;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemRepository;

@Component
public class KisPriceCollector {

    public static final String EVENT_TYPE = "marketdata.price.collect";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MarketDataGateway marketDataGateway;
    private final MarketPriceItemRepository marketPriceItemRepository;
    private final MarketDataOpsEventRecorder opsEventRecorder;

    public KisPriceCollector(
            MarketDataGateway marketDataGateway,
            MarketPriceItemRepository marketPriceItemRepository,
            MarketDataOpsEventRecorder opsEventRecorder) {
        this.marketDataGateway = marketDataGateway;
        this.marketPriceItemRepository = marketPriceItemRepository;
        this.opsEventRecorder = opsEventRecorder;
    }

    public void collect(String symbolCode) {
        try {
            PriceSnapshot snapshot = marketDataGateway.fetchPrice(symbolCode);
            MarketPriceItemEntity entity = new MarketPriceItemEntity();
            entity.setSymbolCode(snapshot.symbolCode());
            entity.setSnapshotAt(snapshot.snapshotAt());
            entity.setBusinessDate(snapshot.snapshotAt().atZoneSameInstant(KST).toLocalDate());
            entity.setLastPrice(snapshot.lastPrice());
            entity.setOpenPrice(snapshot.openPrice());
            entity.setHighPrice(snapshot.highPrice());
            entity.setLowPrice(snapshot.lowPrice());
            entity.setVolume(snapshot.volume());
            entity.setSourceName(snapshot.sourceName());
            marketPriceItemRepository.saveAndFlush(entity);
            opsEventRecorder.recordSuccess(
                    MarketDataOpsEventRecorder.SERVICE_MARKETDATA,
                    EVENT_TYPE,
                    symbolCode);
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
}
