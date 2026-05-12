package work.jscraft.alt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.collector.application.disclosure.DartDisclosureCollector;
import work.jscraft.alt.collector.application.macro.MacroCollector;
import work.jscraft.alt.collector.application.news.NaverNewsCollector;
import work.jscraft.alt.disclosure.application.DisclosureGatewayException;
import work.jscraft.alt.macro.application.MacroGatewayException;
import work.jscraft.alt.news.application.NewsGatewayException;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentCollectorOpsEventTest extends CollectorIntegrationTestSupport {

    @Autowired
    private NaverNewsCollector naverNewsCollector;

    @Autowired
    private DartDisclosureCollector dartDisclosureCollector;

    @Autowired
    private MacroCollector macroCollector;

    @Autowired
    private OpsEventRepository opsEventRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
        fakeNewsGateway.resetAll();
        fakeDisclosureGateway.resetAll();
        fakeMacroGateway.resetAll();
    }

    @Test
    void newsGatewayFailureRecordsDownOpsEvent() {
        fakeNewsGateway.primeNewsFailure(
                "005930",
                new NewsGatewayException(NewsGatewayException.Category.TIMEOUT, "naver", "뉴스 API 타임아웃"));

        assertThatThrownBy(() -> naverNewsCollector.collect("005930",
                OffsetDateTime.of(2026, 5, 10, 0, 0, 0, 0, ZoneOffset.UTC)))
                .isInstanceOf(NewsGatewayException.class);

        List<OpsEventEntity> events = opsEventRepository.findAll();
        assertThat(events).hasSize(1);
        OpsEventEntity event = events.get(0);
        assertThat(event.getServiceName()).isEqualTo(MarketDataOpsEventRecorder.SERVICE_NEWS);
        assertThat(event.getStatusCode()).isEqualTo(MarketDataOpsEventRecorder.STATUS_DOWN);
        assertThat(event.getPayloadJson().path("category").asText()).isEqualTo("TIMEOUT");
    }

    @Test
    void disclosureGatewayFailureRecordsDownOpsEvent() {
        fakeDisclosureGateway.primeFailure(
                "00126380",
                new DisclosureGatewayException(
                        DisclosureGatewayException.Category.TRANSIENT, "dart", "DART 일시 장애"));

        assertThatThrownBy(() -> dartDisclosureCollector.collect("00126380",
                OffsetDateTime.of(2026, 5, 10, 0, 0, 0, 0, ZoneOffset.UTC)))
                .isInstanceOf(DisclosureGatewayException.class);

        List<OpsEventEntity> events = opsEventRepository.findAll();
        assertThat(events).hasSize(1);
        OpsEventEntity event = events.get(0);
        assertThat(event.getServiceName()).isEqualTo(MarketDataOpsEventRecorder.SERVICE_DISCLOSURE);
        assertThat(event.getStatusCode()).isEqualTo(MarketDataOpsEventRecorder.STATUS_DOWN);
        assertThat(event.getPayloadJson().path("category").asText()).isEqualTo("TRANSIENT");
    }

    @Test
    void macroGatewayFailureRecordsDownOpsEvent() {
        LocalDate baseDate = LocalDate.of(2026, 5, 11);
        fakeMacroGateway.primeFailure(
                baseDate,
                new MacroGatewayException(MacroGatewayException.Category.INVALID_RESPONSE, "yfinance",
                        "응답 형식 오류"));

        assertThatThrownBy(() -> macroCollector.collect(baseDate))
                .isInstanceOf(MacroGatewayException.class);

        List<OpsEventEntity> events = opsEventRepository.findAll();
        assertThat(events).hasSize(1);
        OpsEventEntity event = events.get(0);
        assertThat(event.getServiceName()).isEqualTo(MarketDataOpsEventRecorder.SERVICE_MACRO);
        assertThat(event.getStatusCode()).isEqualTo(MarketDataOpsEventRecorder.STATUS_DOWN);
        assertThat(event.getPayloadJson().path("category").asText()).isEqualTo("INVALID_RESPONSE");
    }
}
