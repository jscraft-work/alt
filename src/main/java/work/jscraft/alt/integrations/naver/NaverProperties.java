package work.jscraft.alt.integrations.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.naver")
public class NaverProperties {

    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";

    private String clientId;
    private String clientSecret;
    private String baseUrl = "https://openapi.naver.com";
    private int searchTimeoutSeconds = 8;
    private int articleTimeoutSeconds = 8;
    private int displayLimit = 30;
    private String userAgent = DEFAULT_USER_AGENT;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getSearchTimeoutSeconds() {
        return searchTimeoutSeconds;
    }

    public void setSearchTimeoutSeconds(int searchTimeoutSeconds) {
        this.searchTimeoutSeconds = searchTimeoutSeconds;
    }

    public int getArticleTimeoutSeconds() {
        return articleTimeoutSeconds;
    }

    public void setArticleTimeoutSeconds(int articleTimeoutSeconds) {
        this.articleTimeoutSeconds = articleTimeoutSeconds;
    }

    public int getDisplayLimit() {
        return displayLimit;
    }

    public void setDisplayLimit(int displayLimit) {
        this.displayLimit = displayLimit;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
