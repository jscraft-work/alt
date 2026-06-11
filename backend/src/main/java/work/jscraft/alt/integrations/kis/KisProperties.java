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

        // 21000(실전) 핸드셰이크가 운영 IP에서 거부되어 일단 31000(모의)로 기본값. 호가는 동일 실시간 시장데이터.
        private String wsUrl = "ws://ops.koreainvestment.com:31000";
        private int approvalTimeoutSeconds = 10;
        private String approvalCacheKeyPrefix = "kis:ws:approval:";
        private int approvalSafetyMarginSeconds = 3600;
        private int connectTimeoutSeconds = 10;
        private long reconnectInitialBackoffMillis = 1000L;
        private long reconnectMaxBackoffMillis = 30000L;
        // 장중 세션 내 연속 연결 실패가 이 횟수에 도달하면 재시도를 중단하고 경보. 0 이하면 무한 재시도.
        // (세션은 09:00 연결 / 15:30 종료로 매일 새로 시작하므로, 중단돼도 다음 장에 자동 복구된다.)
        private int reconnectMaxAttempts = 20;
        private int pingIntervalSeconds = 30;

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

        public int getReconnectMaxAttempts() {
            return reconnectMaxAttempts;
        }

        public void setReconnectMaxAttempts(int reconnectMaxAttempts) {
            this.reconnectMaxAttempts = reconnectMaxAttempts;
        }

        public int getPingIntervalSeconds() {
            return pingIntervalSeconds;
        }

        public void setPingIntervalSeconds(int pingIntervalSeconds) {
            this.pingIntervalSeconds = pingIntervalSeconds;
        }
    }
}
