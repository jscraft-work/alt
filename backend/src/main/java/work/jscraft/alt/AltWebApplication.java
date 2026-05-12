package work.jscraft.alt;

import org.springframework.boot.builder.SpringApplicationBuilder;

import work.jscraft.alt.common.config.ApplicationProfiles;

public final class AltWebApplication {

    private AltWebApplication() {
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(AltApplication.class)
                .profiles(ApplicationProfiles.WEB_APP)
                .run(args);
    }
}
