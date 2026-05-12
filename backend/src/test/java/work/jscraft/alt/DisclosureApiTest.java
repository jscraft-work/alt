package work.jscraft.alt;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemEntity;
import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DisclosureApiTest extends AdminCatalogApiIntegrationTestSupport {

    @Autowired
    private DisclosureItemRepository disclosureItemRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
    }

    @Test
    void listFiltersBySymbolAndDateRange() throws Exception {
        createAsset("005930", "삼성전자", "00126380");
        createAsset("000660", "SK하이닉스", "00164779");

        seedDisclosure("00126380", "주요사항보고서",
                OffsetDateTime.of(2026, 5, 9, 22, 10, 0, 0, ZoneOffset.UTC));
        seedDisclosure("00164779", "분기보고서",
                OffsetDateTime.of(2026, 5, 9, 22, 30, 0, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/disclosures").param("symbolCode", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].dartCorpCode").value("00126380"))
                .andExpect(jsonPath("$.data[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data[0].symbolName").value("삼성전자"))
                .andExpect(jsonPath("$.data[0].title").value("주요사항보고서"))
                .andExpect(jsonPath("$.data[0].publishedAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
    }

    @Test
    void listAppliesDefault7DayWindow() throws Exception {
        createAsset("005930", "삼성전자", "00126380");

        seedDisclosure("00126380", "오늘 공시",
                OffsetDateTime.of(2026, 5, 11, 0, 0, 0, 0, ZoneOffset.UTC));
        seedDisclosure("00126380", "10일 전 공시",
                OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/disclosures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("오늘 공시"));
    }

    @Test
    void listFiltersByDartCorpCodeDirectly() throws Exception {
        seedDisclosure("00126380", "삼성 공시",
                OffsetDateTime.of(2026, 5, 9, 22, 10, 0, 0, ZoneOffset.UTC));
        seedDisclosure("00164779", "SK 공시",
                OffsetDateTime.of(2026, 5, 9, 22, 30, 0, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/disclosures").param("dartCorpCode", "00164779"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("SK 공시"));
    }

    private void seedDisclosure(String dartCorpCode, String title, OffsetDateTime publishedAt) {
        DisclosureItemEntity disclosure = new DisclosureItemEntity();
        disclosure.setDartCorpCode(dartCorpCode);
        disclosure.setDisclosureNo("D-" + UUID.randomUUID());
        disclosure.setTitle(title);
        disclosure.setPublishedAt(publishedAt);
        disclosure.setPreviewText(title + " 본문 미리보기");
        disclosure.setDocumentUrl("https://dart.fss.or.kr/" + UUID.randomUUID());
        disclosureItemRepository.saveAndFlush(disclosure);
    }
}
