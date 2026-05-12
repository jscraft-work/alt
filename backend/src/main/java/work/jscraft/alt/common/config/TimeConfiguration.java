package work.jscraft.alt.common.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

    private final AppProperties appProperties;

    public TimeConfiguration(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    void applyDefaultTimezone() {
        ZoneId zoneId = ZoneId.of(appProperties.getTimezone());
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        System.setProperty("user.timezone", zoneId.getId());
    }

    @Bean
    Clock appClock() {
        return Clock.system(ZoneId.of(appProperties.getTimezone()));
    }
}
