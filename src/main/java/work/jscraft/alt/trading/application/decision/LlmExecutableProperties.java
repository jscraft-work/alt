package work.jscraft.alt.trading.application.decision;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.llm")
public class LlmExecutableProperties {

    private final Provider openclaw = new Provider();
    private final Provider nanobot = new Provider();

    public Provider getOpenclaw() {
        return openclaw;
    }

    public Provider getNanobot() {
        return nanobot;
    }

    public Provider forProvider(String name) {
        return switch (name) {
            case "openclaw" -> openclaw;
            case "nanobot" -> nanobot;
            default -> throw new IllegalArgumentException("지원하지 않는 LLM provider: " + name);
        };
    }

    public static class Provider {
        private String command;
        private int timeoutSeconds;

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
