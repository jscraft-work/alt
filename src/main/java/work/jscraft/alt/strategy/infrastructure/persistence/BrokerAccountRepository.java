package work.jscraft.alt.strategy.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BrokerAccountRepository extends JpaRepository<BrokerAccountEntity, UUID> {

    Optional<BrokerAccountEntity> findByBrokerCodeAndBrokerAccountNo(String brokerCode, String brokerAccountNo);
}
