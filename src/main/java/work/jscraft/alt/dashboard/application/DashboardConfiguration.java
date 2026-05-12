package work.jscraft.alt.dashboard.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import work.jscraft.alt.dashboard.domain.SystemStatusClassifier;

@Configuration
public class DashboardConfiguration {

    @Bean
    SystemStatusClassifier systemStatusClassifier() {
        return new SystemStatusClassifier();
    }
}
