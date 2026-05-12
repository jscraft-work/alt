package work.jscraft.alt.news.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NewsItemRepository extends JpaRepository<NewsItemEntity, UUID>, JpaSpecificationExecutor<NewsItemEntity> {

    Optional<NewsItemEntity> findByProviderNameAndExternalNewsId(String providerName, String externalNewsId);

    List<NewsItemEntity> findByUsefulnessStatus(String usefulnessStatus);
}
