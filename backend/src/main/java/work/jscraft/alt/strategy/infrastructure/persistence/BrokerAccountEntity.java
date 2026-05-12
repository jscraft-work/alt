package work.jscraft.alt.strategy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import work.jscraft.alt.common.persistence.SoftDeletableUuidEntity;

@Entity
@Table(name = "broker_account")
@SQLDelete(sql = """
        UPDATE broker_account
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = ? AND version = ?
        """)
@SQLRestriction("deleted_at is null")
public class BrokerAccountEntity extends SoftDeletableUuidEntity {

    @Column(name = "broker_code", nullable = false, length = 40)
    private String brokerCode;

    @Column(name = "broker_account_no", nullable = false, length = 120)
    private String brokerAccountNo;

    @Column(name = "account_alias", length = 120)
    private String accountAlias;

    @Column(name = "account_masked", nullable = false, length = 120)
    private String accountMasked;

    public String getBrokerCode() {
        return brokerCode;
    }

    public void setBrokerCode(String brokerCode) {
        this.brokerCode = brokerCode;
    }

    public String getBrokerAccountNo() {
        return brokerAccountNo;
    }

    public void setBrokerAccountNo(String brokerAccountNo) {
        this.brokerAccountNo = brokerAccountNo;
    }

    public String getAccountAlias() {
        return accountAlias;
    }

    public void setAccountAlias(String accountAlias) {
        this.accountAlias = accountAlias;
    }

    public String getAccountMasked() {
        return accountMasked;
    }

    public void setAccountMasked(String accountMasked) {
        this.accountMasked = accountMasked;
    }
}
