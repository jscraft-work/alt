package work.jscraft.alt.trading.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import work.jscraft.alt.common.config.ApplicationProfiles;

@Configuration
@Profile(ApplicationProfiles.TRADING_WORKER)
@EnableScheduling
public class TradingSchedulingConfiguration {
}
