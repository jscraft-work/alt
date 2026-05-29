package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketMinuteItemRepository extends JpaRepository<MarketMinuteItemEntity, UUID> {

    List<MarketMinuteItemEntity> findBySymbolCodeAndBarTimeBetweenOrderByBarTimeAsc(
            String symbolCode,
            OffsetDateTime barTimeFrom,
            OffsetDateTime barTimeTo);

    boolean existsBySymbolCodeAndBarTimeAndSourceName(String symbolCode, OffsetDateTime barTime, String sourceName);

    @Query("SELECT MAX(m.barTime) FROM MarketMinuteItemEntity m")
    Optional<OffsetDateTime> findMaxBarTime();
}
