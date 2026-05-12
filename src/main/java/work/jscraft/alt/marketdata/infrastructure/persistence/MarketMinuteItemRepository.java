package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMinuteItemRepository extends JpaRepository<MarketMinuteItemEntity, UUID> {

    List<MarketMinuteItemEntity> findBySymbolCodeAndBarTimeBetweenOrderByBarTimeAsc(
            String symbolCode,
            OffsetDateTime barTimeFrom,
            OffsetDateTime barTimeTo);

    boolean existsBySymbolCodeAndBarTimeAndSourceName(String symbolCode, OffsetDateTime barTime, String sourceName);
}
