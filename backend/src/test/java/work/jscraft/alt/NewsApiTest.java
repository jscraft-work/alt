package work.jscraft.alt;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsAssetRelationRepository;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemEntity;
import work.jscraft.alt.news.infrastructure.persistence.NewsItemRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NewsApiTest extends AdminCatalogApiIntegrationTestSupport {

    @Autowired
    private NewsItemRepository newsItemRepository;

    @Autowired
    private NewsAssetRelationRepository newsAssetRelationRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T02:00:00Z"));
    }

    @Test
    void listFiltersByUsefulnessStatusAndDateAndExposesRelatedAssets() throws Exception {
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", "00126380");

        UUID usefulId = seedNews("naver", "삼성전자 실적 기대감", "useful",
                OffsetDateTime.of(2026, 5, 8, 23, 40, 0, 0, ZoneOffset.UTC), samsung);
        seedNews("naver", "오늘의 시황", "not_useful",
                OffsetDateTime.of(2026, 5, 9, 0, 5, 0, 0, ZoneOffset.UTC), null);

        // 7일 범위 밖
        seedNews("naver", "오래된 기사", "useful",
                OffsetDateTime.of(2026, 5, 1, 0, 5, 0, 0, ZoneOffset.UTC), samsung);

        mockMvc.perform(get("/api/news").param("usefulnessStatus", "useful"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(usefulId.toString()))
                .andExpect(jsonPath("$.data[0].title").value("삼성전자 실적 기대감"))
                .andExpect(jsonPath("$.data[0].relatedAssets.length()").value(1))
                .andExpect(jsonPath("$.data[0].relatedAssets[0].symbolCode").value("005930"))
                .andExpect(jsonPath("$.data[0].relatedAssets[0].symbolName").value("삼성전자"))
                .andExpect(jsonPath("$.data[0].publishedAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.meta.size").value(20));
    }

    @Test
    void listFiltersBySymbolCode() throws Exception {
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", "00126380");
        AssetMasterEntity sk = createAsset("000660", "SK하이닉스", "00164779");

        seedNews("naver", "삼성전자 실적", "useful",
                OffsetDateTime.of(2026, 5, 9, 1, 0, 0, 0, ZoneOffset.UTC), samsung);
        seedNews("naver", "SK 실적", "useful",
                OffsetDateTime.of(2026, 5, 9, 2, 0, 0, 0, ZoneOffset.UTC), sk);

        mockMvc.perform(get("/api/news").param("symbolCode", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("삼성전자 실적"));
    }

    @Test
    void listFiltersByDateRange() throws Exception {
        seedNews("naver", "5월 7일", "useful",
                OffsetDateTime.of(2026, 5, 7, 1, 0, 0, 0, ZoneOffset.UTC), null);
        seedNews("naver", "5월 9일", "useful",
                OffsetDateTime.of(2026, 5, 9, 1, 0, 0, 0, ZoneOffset.UTC), null);

        mockMvc.perform(get("/api/news")
                        .param("dateFrom", "2026-05-09")
                        .param("dateTo", "2026-05-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("5월 9일"));
    }

    private UUID seedNews(
            String provider,
            String title,
            String usefulness,
            OffsetDateTime publishedAt,
            AssetMasterEntity asset) {
        NewsItemEntity news = new NewsItemEntity();
        news.setProviderName(provider);
        news.setTitle(title);
        news.setArticleUrl("https://example.com/" + UUID.randomUUID());
        news.setPublishedAt(publishedAt);
        news.setSummary(title + " 요약");
        news.setUsefulnessStatus(usefulness);
        news = newsItemRepository.saveAndFlush(news);

        if (asset != null) {
            NewsAssetRelationEntity rel = new NewsAssetRelationEntity();
            rel.setNewsItemId(news.getId());
            rel.setAssetMasterId(asset.getId());
            newsAssetRelationRepository.saveAndFlush(rel);
        }
        return news.getId();
    }
}
