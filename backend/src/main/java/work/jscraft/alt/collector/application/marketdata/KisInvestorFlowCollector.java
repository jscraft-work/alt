package work.jscraft.alt.collector.application.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.application.MarketDataGateway;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.InvestorFlowRow;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.InvestorFlowSnapshot;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketInvestorFlowItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketInvestorFlowItemRepository;

/**
 * KIS inquire-investor 응답(일자별 개인/외국인/기관계 순매수)을 종목별로 수집해
 * {@code market_investor_flow_item} 에 일자 단위 upsert 한다. (종목+일자+소스 유니크 → 멱등)
 */
@Component
public class KisInvestorFlowCollector {

    public static final String EVENT_TYPE = "marketdata.investorflow.collect";
    private static final Logger log = LoggerFactory.getLogger(KisInvestorFlowCollector.class);

    private final MarketDataGateway gateway;
    private final MarketInvestorFlowItemRepository repository;
    private final MarketDataOpsEventRecorder opsEventRecorder;

    public KisInvestorFlowCollector(
            MarketDataGateway gateway,
            MarketInvestorFlowItemRepository repository,
            MarketDataOpsEventRecorder opsEventRecorder) {
        this.gateway = gateway;
        this.repository = repository;
        this.opsEventRecorder = opsEventRecorder;
    }

    public void collect(String symbolCode) {
        try {
            InvestorFlowSnapshot snap = gateway.fetchInvestorFlow(symbolCode);
            for (InvestorFlowRow row : snap.rows()) {
                MarketInvestorFlowItemEntity entity = repository
                        .findBySymbolCodeAndTradeDateAndSourceName(
                                snap.symbolCode(), row.tradeDate(), snap.sourceName())
                        .orElseGet(MarketInvestorFlowItemEntity::new);

                entity.setSymbolCode(snap.symbolCode());
                entity.setTradeDate(row.tradeDate());
                entity.setSourceName(snap.sourceName());
                entity.setClosePrice(row.closePrice());
                entity.setIndvNtbyQty(row.indvNtbyQty());
                entity.setFrgnNtbyQty(row.frgnNtbyQty());
                entity.setOrgnNtbyQty(row.orgnNtbyQty());
                entity.setIndvNtbyAmt(row.indvNtbyAmt());
                entity.setFrgnNtbyAmt(row.frgnNtbyAmt());
                entity.setOrgnNtbyAmt(row.orgnNtbyAmt());

                repository.save(entity);
            }
            repository.flush();
            opsEventRecorder.recordSuccess(
                    MarketDataOpsEventRecorder.SERVICE_MARKETDATA, EVENT_TYPE, symbolCode);
            log.debug("investorflow collected symbol={} rows={}", symbolCode, snap.rows().size());
        } catch (MarketDataException ex) {
            opsEventRecorder.recordFailure(
                    MarketDataOpsEventRecorder.SERVICE_MARKETDATA, EVENT_TYPE,
                    symbolCode, ex.getMessage(), ex.getCategory().name());
            throw ex;
        }
    }
}
