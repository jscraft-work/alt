package work.jscraft.alt.ops.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    /**
     * F4 — admin/audit-log read-only 페이지 조회.
     *
     * <p>모든 필터는 비 null 센티넬로 전달 (PostgreSQL {@code :param IS NULL} 패턴 회피).
     * 빈 문자열 = 해당 컬럼 필터 미적용.
     *
     * @param from         exit_time-style 센티넬: 미적용 시 매우 이른 시각 (예: 1900-01-01)
     * @param to           미적용 시 매우 먼 미래 (예: 9999-12-31)
     * @param targetType   빈 문자열 = 미적용
     * @param actorType    빈 문자열 = 미적용
     * @param actionType   빈 문자열 = 미적용
     */
    @Query(value = """
            SELECT a FROM AuditLogEntity a
            WHERE a.occurredAt >= :from
              AND a.occurredAt < :to
              AND ('' = :targetType OR a.targetType = :targetType)
              AND ('' = :actorType OR a.actorType = :actorType)
              AND ('' = :actionType OR a.actionType = :actionType)
            ORDER BY a.occurredAt DESC
            """,
           countQuery = """
            SELECT COUNT(a) FROM AuditLogEntity a
            WHERE a.occurredAt >= :from
              AND a.occurredAt < :to
              AND ('' = :targetType OR a.targetType = :targetType)
              AND ('' = :actorType OR a.actorType = :actorType)
              AND ('' = :actionType OR a.actionType = :actionType)
            """)
    Page<AuditLogEntity> findFiltered(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("targetType") String targetType,
            @Param("actorType") String actorType,
            @Param("actionType") String actionType,
            Pageable pageable);
}
