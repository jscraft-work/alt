package work.jscraft.alt.news.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsAssetRelationRepository extends JpaRepository<NewsAssetRelationEntity, UUID> {

    List<NewsAssetRelationEntity> findByNewsItemIdIn(Collection<UUID> newsItemIds);

    List<NewsAssetRelationEntity> findByAssetMasterId(UUID assetMasterId);
}
