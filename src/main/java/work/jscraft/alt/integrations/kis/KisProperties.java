package work.jscraft.alt.integrations.kis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kis")
public class KisProperties {

    private String appKey;
    private String appSecret;
    private String baseUrl = "https://openapi.koreainvestment.com:9443";
    private String environment = "real";
    private int restTimeoutSeconds = 8;
    private int tokenTimeoutSeconds = 10;
    private int tokenSafetyMarginSeconds = 300;
    private String tokenCacheKeyPrefix = "kis:token:";
    private WebSocket websocket = new WebSocket();

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public int getRestTimeoutSeconds() {
        return restTimeoutSeconds;
    }

    public void setRestTimeoutSeconds(int restTimeoutSeconds) {
        this.restTimeoutSeconds = restTimeoutSeconds;
    }

    public int getTokenTimeoutSeconds() {
        return tokenTimeoutSeconds;
    }

    public void setTokenTimeoutSeconds(int tokenTimeoutSeconds) {
        this.tokenTimeoutSeconds = tokenTimeoutSeconds;
    }

    public int getTokenSafetyMarginSeconds() {
        return tokenSafetyMarginSeconds;
    }

    public void setTokenSafetyMarginSeconds(int tokenSafetyMarginSeconds) {
        this.tokenSafetyMarginSeconds = tokenSafetyMarginSeconds;
    }

    public String getTokenCacheKeyPrefix() {
        return tokenCacheKeyPrefix;
    }

    public void setTokenCacheKeyPrefix(String tokenCacheKeyPrefix) {
        this.tokenCacheKeyPrefix = tokenCacheKeyPrefix;
    }

    public WebSocket getWebsocket() {
        return websocket;
    }

    public void setWebsocket(WebSocket websocket) {
        this.websocket = websocket;
    }

    public static class WebSocket {

        private boolean enabled = false;
        private String wsUrl = "ws://ops.koreainvestment.com:21000";
        private int approvalTimeoutSeconds = 10;
        private String approvalCacheKeyPrefix = "kis:ws:approval:";
        private int approvalSafetyMarginSeconds = 3600;
        private int connectTimeoutSeconds = 10;
        private long reconnectInitialBackoffMillis = 1000L;
        private long reconnectMaxBackoffMillis = 30000L;
        private int pingIntervalSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWsUrl() {
            return wsUrl;
        }

        public void setWsUrl(String wsUrl) {
            this.wsUrl = wsUrl;
        }

        public int getApprovalTimeoutSeconds() {
            return approvalTimeoutSeconds;
        }

        public void setApprovalTimeoutSeconds(int approvalTimeoutSeconds) {
            this.approvalTimeoutSeconds = approvalTimeoutSeconds;
        }

        public String getApprovalCacheKeyPrefix() {
            return approvalCacheKeyPrefix;
        }

        public void setApprovalCacheKeyPrefix(String approvalCacheKeyPrefix) {
            this.approvalCacheKeyPrefix = approvalCacheKeyPrefix;
        }

        public int getApprovalSafetyMarginSeconds() {
            return approvalSafetyMarginSeconds;
        }

        public void setApprovalSafetyMarginSeconds(int approvalSafetyMarginSeconds) {
            this.approvalSafetyMarginSeconds = approvalSafetyMarginSeconds;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public long getReconnectInitialBackoffMillis() {
            return reconnectInitialBackoffMillis;
        }

        public void setReconnectInitialBackoffMillis(long reconnectInitialBackoffMillis) {
            this.reconnectInitialBackoffMillis = reconnectInitialBackoffMillis;
        }

        public long getReconnectMaxBackoffMillis() {
            return reconnectMaxBackoffMillis;
        }

        public void setReconnectMaxBackoffMillis(long reconnectMaxBackoffMillis) {
            this.reconnectMaxBackoffMillis = reconnectMaxBackoffMillis;
        }

        public int getPingIntervalSeconds() {
            return pingIntervalSeconds;
        }

        public void setPingIntervalSeconds(int pingIntervalSeconds) {
            this.pingIntervalSeconds = pingIntervalSeconds;
        }
    }
}
