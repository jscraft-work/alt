package work.jscraft.alt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import work.jscraft.alt.collector.application.disclosure.DartDisclosureCollector;
import work.jscraft.alt.collector.application.macro.MacroCollector;
import work.jscraft.alt.collector.application.marketdata.KisMinuteBarCollector;
import work.jscraft.alt.collector.application.marketdata.KisPriceCollector;
import work.jscraft.alt.collector.application.marketdata.OrderBookRedisCache;
import work.jscraft.alt.collector.application.marketdata.WebSocketStatusTracker;
import work.jscraft.alt.collector.application.news.ArticleBodyFetcher;
import work.jscraft.alt.collector.application.news.NaverNewsCollector;
import work.jscraft.alt.collector.application.news.NewsAssessmentWorkflow;
import work.jscraft.alt.collector.infrastructure.CollectorSchedulingConfiguration;
import work.jscraft.alt.interfaces.scheduler.ContentCollectorScheduler;
import work.jscraft.alt.interfaces.scheduler.MarketDataCollectorScheduler;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({ PostgreSqlTestConfiguration.class, RedisTestConfiguration.class, AuthTestClockConfiguration.class,
        CollectorTestConfiguration.class })
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("collector-worker")
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.auth.jwt-secret=test-jwt-secret-test-jwt-secret-test-jwt-secret",
        "app.collector.marketdata.initial-delay-ms=3600000",
        "app.collector.marketdata.price-interval-ms=3600000",
        "app.collector.marketdata.minute-bar-interval-ms=3600000",
        "app.collector.content.initial-delay-ms=3600000",
        "app.collector.content.news-interval-ms=3600000",
        "app.collector.content.disclosure-interval-ms=3600000",
        "app.collector.content.macro-interval-ms=3600000",
        "app.seed.enabled=false"
})
class CollectorWorkerBootstrapTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void collectorProfileWiresExpectedCollectorBeans() {
        assertThat(applicationContext.getBean(KisPriceCollector.class)).isNotNull();
        assertThat(applicationContext.getBean(KisMinuteBarCollector.class)).isNotNull();
        assertThat(applicationContext.getBean(OrderBookRedisCache.class)).isNotNull();
        assertThat(applicationContext.getBean(WebSocketStatusTracker.class)).isNotNull();
        assertThat(applicationContext.getBean(NaverNewsCollector.class)).isNotNull();
        assertThat(applicationContext.getBean(ArticleBodyFetcher.class)).isNotNull();
        assertThat(applicationContext.getBean(NewsAssessmentWorkflow.class)).isNotNull();
        assertThat(applicationContext.getBean(DartDisclosureCollector.class)).isNotNull();
        assertThat(applicationContext.getBean(MacroCollector.class)).isNotNull();
        assertThat(applicationContext.getBean(MarketDataCollectorScheduler.class)).isNotNull();
        assertThat(applicationContext.getBean(ContentCollectorScheduler.class)).isNotNull();
        assertThat(applicationContext.getBean(CollectorSchedulingConfiguration.class)).isNotNull();
    }

    @Test
    void collectorProfileIsActive() {
        assertThat(applicationContext.getEnvironment().getActiveProfiles())
                .contains("collector-worker");
    }
}
