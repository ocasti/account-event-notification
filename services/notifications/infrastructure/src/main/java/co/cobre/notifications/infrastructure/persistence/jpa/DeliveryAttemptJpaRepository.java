package co.cobre.notifications.infrastructure.persistence.jpa;

import co.cobre.notifications.infrastructure.persistence.entity.DeliveryAttemptEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface DeliveryAttemptJpaRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {

    @Query(value = "SELECT d.id, e.client_id FROM delivery_attempts d " +
                   "JOIN notification_events e ON e.event_id = d.event_id " +
                   "WHERE d.executed_at IS NULL " +
                   "AND d.next_attempt_at <= now() " +
                   "AND (d.claimed_at IS NULL OR d.claimed_at < now() - make_interval(secs => :leaseSeconds)) " +
                   "ORDER BY d.next_attempt_at ASC " +
                   "LIMIT :limit", nativeQuery = true)
    List<Map<String, Object>> claimDueAttempts(
        @Param("leaseSeconds") long leaseSeconds,
        @Param("limit") int limit
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE delivery_attempts SET claimed_at = now(), claimed_by = :workerId " +
                   "WHERE id IN (SELECT CAST(id AS uuid) FROM (VALUES :ids) AS t(id))",
           nativeQuery = true)
    void updateClaimedBatch(
        @Param("ids") Set<UUID> ids,
        @Param("workerId") String workerId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DeliveryAttemptEntity da " +
           "SET da.executedAt = :executedAt, da.responseStatus = :responseStatus, " +
           "    da.failureReason = :failureReason, da.latencyMs = :latencyMs " +
           "WHERE da.id = :id AND da.claimedBy = :claimedBy AND da.executedAt IS NULL")
    int recordResult(
        @Param("id") UUID id,
        @Param("claimedBy") String claimedBy,
        @Param("executedAt") Instant executedAt,
        @Param("responseStatus") Integer responseStatus,
        @Param("failureReason") String failureReason,
        @Param("latencyMs") Long latencyMs
    );

    List<DeliveryAttemptEntity> findByEventId(String eventId, Sort sort);
}
