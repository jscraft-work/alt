package work.jscraft.alt;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.collector.application.macro.MacroCollector;
import work.jscraft.alt.collector.application.macro.MacroCollector.CollectResult;
import work.jscraft.alt.macro.application.MacroGateway.MacroSnapshot;
import work.jscraft.alt.macro.infrastructure.persistence.MacroItemEntity;
import work.jscraft.alt.macro.infrastructure.persistence.MacroItemRepository;

import static org.assertj.core.api.Assertions.assertThat;

class MacroCollectorTest extends CollectorIntegrationTestSupport {

    @Autowired
    private MacroCollector macroCollector;

    @Autowired
    private MacroItemRepository macroItemRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
        fakeMacroGateway.resetAll();
    }

    @Test
    void collectStoresDailySnapshot() {
        LocalDate baseDate = LocalDate.of(2026, 5, 11);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("usdKrw", 1380.5);
        payload.put("sp500", 5421.2);
        fakeMacroGateway.primeMacro(baseDate, new MacroSnapshot(baseDate, payload));

        CollectResult result = macroCollector.collect(baseDate);

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(0);
        assertThat(macroItemRepository.findAll()).hasSize(1);
        MacroItemEntity row = macroItemRepository.findByBaseDate(baseDate).orElseThrow();
        assertThat(row.getPayloadJson().path("usdKrw").asDouble()).isEqualTo(1380.5);
        assertThat(row.getPayloadJson().path("sp500").asDouble()).isEqualTo(5421.2);
    }

    @Test
    void secondCollectForSameDayOverwritesPayload() {
        LocalDate baseDate = LocalDate.of(2026, 5, 11);
        ObjectNode first = objectMapper.createObjectNode();
        first.put("usdKrw", 1380.5);
        fakeMacroGateway.primeMacro(baseDate, new MacroSnapshot(baseDate, first));
        macroCollector.collect(baseDate);

        ObjectNode second = objectMapper.createObjectNode();
        second.put("usdKrw", 1385.0);
        fakeMacroGateway.primeMacro(baseDate, new MacroSnapshot(baseDate, second));

        CollectResult result = macroCollector.collect(baseDate);

        assertThat(result.saved()).isEqualTo(0);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(macroItemRepository.findAll()).hasSize(1);
        MacroItemEntity row = macroItemRepository.findByBaseDate(baseDate).orElseThrow();
        assertThat(row.getPayloadJson().path("usdKrw").asDouble()).isEqualTo(1385.0);
    }
}
