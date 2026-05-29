package work.jscraft.alt.news.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface NewsItemRepository extends JpaRepository<NewsItemEntity, UUID>, JpaSpecificationExecutor<NewsItemEntity> {

    Optional<NewsItemEntity> findByProviderNameAndExternalNewsId(String providerName, String externalNewsId);

    List<NewsItemEntity> findByUsefulnessStatus(String usefulnessStatus);

    @Query("SELECT MAX(n.publishedAt) FROM NewsItemEntity n")
    Optional<OffsetDateTime> findMaxPublishedAt();
}
