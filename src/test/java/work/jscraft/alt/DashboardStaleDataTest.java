package work.jscraft.alt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.ops.infrastructure.persistence.OpsEventEntity;
import work.jscraft.alt.ops.infrastructure.persistence.OpsEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardStaleDataTest extends AdminCatalogApiIntegrationTestSupport {

    @Autowired
    private OpsEventRepository opsEventRepository;

    private static final Instant NOW_INSTANT = Instant.parse("2026-05-11T02:00:00Z");
    private static final OffsetDateTime NOW_KST = NOW_INSTANT.atOffset(ZoneOffset.ofHours(9));

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(NOW_INSTANT);
    }

    @Test
    void failureAfterSuccessReportsDownAndPreservesLastSuccess() throws Exception {
        OffsetDateTime lastSuccessUtc = NOW_KST.minusMinutes(15).withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime failureUtc = NOW_KST.minusMinutes(5).withOffsetSameInstant(ZoneOffset.UTC);
        recordOpsEvent("marketdata", "ok", lastSuccessUtc, "snapshot ok");
        recordOpsEvent("marketdata", "down", failureUtc, "broker timeout");

        String body = mockMvc.perform(get("/api/dashboard/system-status"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode marketdata = findService(objectMapper.readTree(body).path("data"), "marketdata");
        assertThat(marketdata.path("statusCode").asText()).isEqualTo("down");
        assertThat(marketdata.path("message").asText()).isEqualTo("broker timeout");
        OffsetDateTime serializedLastSuccess = OffsetDateTime.parse(marketdata.path("lastSuccessAt").asText());
        assertThat(serializedLastSuccess.toInstant()).isEqualTo(lastSuccessUtc.toInstant());
        assertThat(marketdata.path("lastSuccessAt").asText()).endsWith("+09:00");
    }

    @Test
    void noEventsReportsDownButLastSuccessIsAbsent() throws Exception {
        String body = mockMvc.perform(get("/api/dashboard/system-status"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode marketdata = findService(objectMapper.readTree(body).path("data"), "marketdata");
        assertThat(marketdata.path("statusCode").asText()).isEqualTo("down");
        assertThat(marketdata.path("lastSuccessAt").isNull()).isTrue();
    }

    @Test
    void delayedReportsRetainsLastSuccessAtForUi() throws Exception {
        OffsetDateTime lastSuccessUtc = NOW_KST.minusMinutes(3).withOffsetSameInstant(ZoneOffset.UTC);
        recordOpsEvent("marketdata", "ok", lastSuccessUtc, "snapshot ok");

        String body = mockMvc.perform(get("/api/dashboard/system-status"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode marketdata = findService(objectMapper.readTree(body).path("data"), "marketdata");
        assertThat(marketdata.path("statusCode").asText()).isEqualTo("delayed");
        assertThat(marketdata.path("lastSuccessAt").asText()).endsWith("+09:00");
    }

    private void recordOpsEvent(String serviceName, String statusCode, OffsetDateTime occurredAt, String message) {
        OpsEventEntity event = new OpsEventEntity();
        event.setServiceName(serviceName);
        event.setStatusCode(statusCode);
        event.setOccurredAt(occurredAt);
        event.setBusinessDate(LocalDate.from(occurredAt.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))));
        event.setEventType("HEALTHCHECK");
        event.setMessage(message);
        opsEventRepository.saveAndFlush(event);
    }

    private JsonNode findService(JsonNode data, String name) {
        for (JsonNode node : data) {
            if (name.equals(node.path("serviceName").asText())) {
                return node;
            }
        }
        throw new AssertionError("serviceName not found: " + name);
    }
}
