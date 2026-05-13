package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketFundamentalItemRepository extends JpaRepository<MarketFundamentalItemEntity, UUID> {

    Optional<MarketFundamentalItemEntity> findFirstBySymbolCodeOrderBySnapshotAtDesc(String symbolCode);

    Optional<MarketFundamentalItemEntity> findBySymbolCodeAndBusinessDateAndSourceName(
            String symbolCode, LocalDate businessDate, String sourceName);
}
