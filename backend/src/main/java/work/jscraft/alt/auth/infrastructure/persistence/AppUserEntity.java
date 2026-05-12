package work.jscraft.alt.auth.infrastructure.persistence;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import work.jscraft.alt.common.persistence.SoftDeletableUuidEntity;

@Entity
@Table(name = "app_user")
@SQLDelete(sql = """
        UPDATE app_user
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = ? AND version = ?
        """)
@SQLRestriction("deleted_at is null")
public class AppUserEntity extends SoftDeletableUuidEntity {

    @Column(name = "login_id", nullable = false, length = 120)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "role_code", nullable = false, length = 40)
    private String roleCode;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(OffsetDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
