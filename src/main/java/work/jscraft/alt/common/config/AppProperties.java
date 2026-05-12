package work.jscraft.alt.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotBlank
    private String timezone = "Asia/Seoul";

    @Valid
    @NotNull
    private final Runtime runtime = new Runtime();

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    public static class Runtime {

        @NotNull
        private RuntimeRole role = RuntimeRole.WEB_APP;

        public RuntimeRole getRole() {
            return role;
        }

        public void setRole(RuntimeRole role) {
            this.role = role;
        }
    }

    public enum RuntimeRole {
        WEB_APP,
        TRADING_WORKER,
        COLLECTOR_WORKER
    }
}
