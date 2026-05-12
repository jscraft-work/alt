package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketPriceItemRepository extends JpaRepository<MarketPriceItemEntity, UUID> {

    Optional<MarketPriceItemEntity> findFirstBySymbolCodeOrderBySnapshotAtDesc(String symbolCode);
}
