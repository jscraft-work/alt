package work.jscraft.alt.collector.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import work.jscraft.alt.common.config.ApplicationProfiles;

@Configuration
@Profile(ApplicationProfiles.COLLECTOR_WORKER)
@EnableScheduling
public class CollectorSchedulingConfiguration {
}
