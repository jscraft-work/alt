package work.jscraft.alt;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AltApplication {

    public static void main(String[] args) {
        AltWebApplication.main(args);
    }
}
