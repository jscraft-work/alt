package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketInvestorFlowItemRepository
        extends JpaRepository<MarketInvestorFlowItemEntity, UUID> {

    Optional<MarketInvestorFlowItemEntity> findBySymbolCodeAndTradeDateAndSourceName(
            String symbolCode, LocalDate tradeDate, String sourceName);
}
