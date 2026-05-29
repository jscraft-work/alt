package work.jscraft.alt.ops.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpsEventRepository extends JpaRepository<OpsEventEntity, UUID> {

    Optional<OpsEventEntity> findFirstByServiceNameAndStatusCodeOrderByOccurredAtDesc(
            String serviceName,
            String statusCode);

    /** 특정 서비스의 최신 이벤트 (status_code 무관). */
    Optional<OpsEventEntity> findFirstByServiceNameOrderByOccurredAtDesc(String serviceName);

    /** 특정 서비스의 최근 N건 이벤트 — admin data-collection 모니터링 표 */
    List<OpsEventEntity> findByServiceNameOrderByOccurredAtDesc(String serviceName, Limit limit);

    /** 특정 서비스의 occurredAt >= since 인 이벤트 개수 — 24h 성공/실패 카운트 */
    @Query("""
            SELECT COUNT(e) FROM OpsEventEntity e
            WHERE e.serviceName = :serviceName
              AND e.statusCode = :statusCode
              AND e.occurredAt >= :since
            """)
    long countByServiceAndStatusSince(
            @Param("serviceName") String serviceName,
            @Param("statusCode") String statusCode,
            @Param("since") OffsetDateTime since);
}
