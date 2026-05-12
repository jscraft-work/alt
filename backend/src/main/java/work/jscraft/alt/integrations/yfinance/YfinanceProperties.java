package work.jscraft.alt.integrations.yfinance;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.yfinance")
public class YfinanceProperties {

    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";

    private String baseUrl = "https://query1.finance.yahoo.com";
    private int perTickerTimeoutSeconds = 6;
    private String userAgent = DEFAULT_USER_AGENT;
    private int parallelism = 6;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getPerTickerTimeoutSeconds() {
        return perTickerTimeoutSeconds;
    }

    public void setPerTickerTimeoutSeconds(int perTickerTimeoutSeconds) {
        this.perTickerTimeoutSeconds = perTickerTimeoutSeconds;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }
}
