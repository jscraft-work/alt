package work.jscraft.alt.trading.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeCycleLogRepository extends JpaRepository<TradeCycleLogEntity, UUID> {
}
