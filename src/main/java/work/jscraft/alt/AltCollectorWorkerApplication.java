package work.jscraft.alt;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import work.jscraft.alt.common.config.ApplicationProfiles;

public final class AltCollectorWorkerApplication {

    private AltCollectorWorkerApplication() {
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(AltApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(ApplicationProfiles.COLLECTOR_WORKER)
                .run(args);
    }
}
