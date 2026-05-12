package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetMasterRepository extends JpaRepository<AssetMasterEntity, UUID> {

    Optional<AssetMasterEntity> findBySymbolCode(String symbolCode);

    Optional<AssetMasterEntity> findByDartCorpCode(String dartCorpCode);
}
