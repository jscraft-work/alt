package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketPriceItemRepository extends JpaRepository<MarketPriceItemEntity, UUID> {

    Optional<MarketPriceItemEntity> findFirstBySymbolCodeOrderBySnapshotAtDesc(String symbolCode);

    @Query("SELECT MAX(m.snapshotAt) FROM MarketPriceItemEntity m")
    Optional<OffsetDateTime> findMaxSnapshotAt();
}
