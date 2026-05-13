package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketDailyItemRepository extends JpaRepository<MarketDailyItemEntity, UUID> {

    List<MarketDailyItemEntity> findBySymbolCodeAndBusinessDateGreaterThanEqualOrderByBusinessDateAsc(
            String symbolCode, LocalDate businessDate);
}
