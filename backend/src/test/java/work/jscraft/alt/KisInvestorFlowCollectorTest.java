package work.jscraft.alt;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.marketdata.KisInvestorFlowCollector;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.InvestorFlowRow;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.InvestorFlowSnapshot;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketInvestorFlowItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketInvestorFlowItemRepository;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;

import static org.assertj.core.api.Assertions.assertThat;

class KisInvestorFlowCollectorTest extends CollectorIntegrationTestSupport {

    @Autowired
    private KisInvestorFlowCollector kisInvestorFlowCollector;

    @Autowired
    private MarketInvestorFlowItemRepository marketInvestorFlowItemRepository;

    @Autowired
    private OpsEventRepository opsEventRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        fakeMarketDataGateway.resetAll();
    }

    @Test
    void collectPersistsDailyRowsAndRecordsSuccess() {
        fakeMarketDataGateway.primeInvestorFlow("005930", new InvestorFlowSnapshot(
                "005930", "kis", List.of(
                        new InvestorFlowRow(LocalDate.of(2026, 6, 10), 38150,
                                643956L, -393694L, -268096L, 24451L, -14906L, -10218L),
                        new InvestorFlowRow(LocalDate.of(2026, 6, 9), 39500,
                                -425624L, 333348L, 93011L, -16841L, 13188L, 3684L))));

        kisInvestorFlowCollector.collect("005930");

        assertThat(marketInvestorFlowItemRepository.findAll()).hasSize(2);
        MarketInvestorFlowItemEntity row = marketInvestorFlowItemRepository
                .findBySymbolCodeAndTradeDateAndSourceName("005930", LocalDate.of(2026, 6, 10), "kis")
                .orElseThrow();
        assertThat(row.getClosePrice()).isEqualTo(38150);
        assertThat(row.getIndvNtbyQty()).isEqualTo(643956L);
        assertThat(row.getFrgnNtbyQty()).isEqualTo(-393694L);
        assertThat(row.getOrgnNtbyQty()).isEqualTo(-268096L);
        assertThat(row.getOrgnNtbyAmt()).isEqualTo(-10218L);

        List<OpsEventEntity> events = opsEventRepository.findAll().stream()
                .filter(e -> KisInvestorFlowCollector.EVENT_TYPE.equals(e.getEventType()))
                .toList();
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getServiceName())
                .isEqualTo(MarketDataOpsEventRecorder.SERVICE_MARKETDATA);
        assertThat(events.get(0).getStatusCode())
                .isEqualTo(MarketDataOpsEventRecorder.STATUS_OK);
    }

    @Test
    void collectIsIdempotentOnReRun() {
        InvestorFlowSnapshot snap = new InvestorFlowSnapshot("005930", "kis", List.of(
                new InvestorFlowRow(LocalDate.of(2026, 6, 10), 38150,
                        643956L, -393694L, -268096L, 24451L, -14906L, -10218L)));
        fakeMarketDataGateway.primeInvestorFlow("005930", snap);

        kisInvestorFlowCollector.collect("005930");
        // 같은 일자 데이터 재수집 → 덮어쓰기, 중복 row 안 생김
        kisInvestorFlowCollector.collect("005930");

        assertThat(marketInvestorFlowItemRepository.findAll()).hasSize(1);
    }
}
