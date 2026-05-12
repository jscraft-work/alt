package work.jscraft.alt;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import work.jscraft.alt.common.config.ApplicationProfiles;

public final class AltTradingWorkerApplication {

    private AltTradingWorkerApplication() {
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(AltApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(ApplicationProfiles.TRADING_WORKER)
                .run(args);
    }
}
