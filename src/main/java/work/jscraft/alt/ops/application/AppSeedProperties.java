package work.jscraft.alt.ops.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed")
public class AppSeedProperties {

    private boolean enabled = false;
    private final Admin admin = new Admin();
    private final ModelProfile modelProfile = new ModelProfile();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Admin getAdmin() {
        return admin;
    }

    public ModelProfile getModelProfile() {
        return modelProfile;
    }

    public static class Admin {
        private String loginId = "admin";
        private String password = "ChangeMe!2026";
        private String displayName = "관리자";

        public String getLoginId() {
            return loginId;
        }

        public void setLoginId(String loginId) {
            this.loginId = loginId;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class ModelProfile {
        private String purpose = "trading_decision";
        private String provider = "openclaw";
        private String modelName = "default";

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(String purpose) {
            this.purpose = purpose;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }
}
