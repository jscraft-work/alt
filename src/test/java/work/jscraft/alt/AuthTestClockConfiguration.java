package work.jscraft.alt;

import java.time.Instant;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
class AuthTestClockConfiguration {

    static final Instant DEFAULT_INSTANT = Instant.parse("2026-05-09T00:00:00Z");

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(DEFAULT_INSTANT, ZoneId.of("Asia/Seoul"));
    }
}
