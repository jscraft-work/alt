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

import work.jscraft.alt.interfaces.scheduler.TradingCycleScheduler;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshotProvider;
import work.jscraft.alt.trading.application.cycle.TradeCycleLifecycle;
import work.jscraft.alt.trading.application.cycle.TradingInstanceLock;
import work.jscraft.alt.trading.application.cycle.TradingWindowPolicy;
import work.jscraft.alt.trading.application.inputspec.InputAssembler;
import work.jscraft.alt.trading.infrastructure.TradingSchedulingConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({ PostgreSqlTestConfiguration.class, RedisTestConfiguration.class, AuthTestClockConfiguration.class })
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("trading-worker")
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.auth.jwt-secret=test-jwt-secret-test-jwt-secret-test-jwt-secret",
        "app.trading.cycle.initial-delay-ms=3600000",
        "app.trading.cycle.poll-interval-ms=3600000",
        "app.seed.enabled=false"
})
class TradingWorkerBootstrapTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void tradingProfileWiresExpectedCycleBeans() {
        assertThat(applicationContext.getBean(TradingWindowPolicy.class)).isNotNull();
        assertThat(applicationContext.getBean(TradingInstanceLock.class)).isNotNull();
        assertThat(applicationContext.getBean(TradeCycleLifecycle.class)).isNotNull();
        assertThat(applicationContext.getBean(SettingsSnapshotProvider.class)).isNotNull();
        assertThat(applicationContext.getBean(CycleExecutionOrchestrator.class)).isNotNull();
        assertThat(applicationContext.getBean(InputAssembler.class)).isNotNull();
        assertThat(applicationContext.getBean(TradingCycleScheduler.class)).isNotNull();
        assertThat(applicationContext.getBean(TradingSchedulingConfiguration.class)).isNotNull();
    }

    @Test
    void tradingProfileIsActive() {
        assertThat(applicationContext.getEnvironment().getActiveProfiles()).contains("trading-worker");
    }
}
