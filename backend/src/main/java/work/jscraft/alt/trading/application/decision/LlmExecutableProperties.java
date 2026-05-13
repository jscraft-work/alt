package work.jscraft.alt.trading.application.decision;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM HTTP wrapper config. The wrapper runs on the deploy host (macmini) and is reached at
 * {@code base-url}; provider selection is conveyed as a {@code level} field in the POST body
 * (openclaw → "normal", nanobot → "high").
 *
 * <p>Replaces the previous subprocess-based config (host binary + args). The trading worker now
 * calls the wrapper over HTTP via {@code host.docker.internal:18000}.
 */
@ConfigurationProperties(prefix = "app.llm")
public class LlmExecutableProperties {

    private String baseUrl;
    private final Provider openclaw = new Provider();
    private final Provider nanobot = new Provider();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

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

    /** Maps the persisted provider name to the wrapper's {@code level} field. */
    public static String levelOf(String providerName) {
        return switch (providerName) {
            case "openclaw" -> "normal";
            case "nanobot" -> "high";
            default -> throw new IllegalArgumentException("지원하지 않는 LLM provider: " + providerName);
        };
    }

    public static class Provider {
        private int timeoutSeconds;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
