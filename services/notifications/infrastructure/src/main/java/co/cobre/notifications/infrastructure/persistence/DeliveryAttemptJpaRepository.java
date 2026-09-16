package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DeliveryAttemptJpaRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {

    @Query(value = "SELECT d.id, e.client_id FROM delivery_attempts d " +
                   "JOIN notification_events e ON e.event_id = d.event_id " +
                   "WHERE d.executed_at IS NULL " +
                   "AND d.next_attempt_at <= now() " +
                   "AND (d.claimed_at IS NULL OR d.claimed_at < now() - make_interval(secs => :leaseSeconds)) " +
                   "ORDER BY d.next_attempt_at ASC " +
                   "LIMIT :limit " +
                   "FOR UPDATE OF d SKIP LOCKED", nativeQuery = true)
    List<Map<String, Object>> claimDueAttempts(
        @Param("leaseSeconds") long leaseSeconds,
        @Param("limit") int limit
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE delivery_attempts SET claimed_at = now(), claimed_by = :workerId " +
                   "WHERE id IN (:ids)",
           nativeQuery = true)
    int updateClaimedBatch(
        @Param("ids") List<UUID> ids,
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

    @Query(value = "SELECT count(*) FROM delivery_attempts WHERE executed_at IS NULL AND next_attempt_at <= now() AND claimed_at IS NULL", nativeQuery = true)
    long countDue();

    @Query("SELECT d.eventId, COUNT(d) FROM DeliveryAttemptEntity d WHERE d.eventId IN :eventIds GROUP BY d.eventId")
    List<Object[]> countByEventIds(@Param("eventIds") List<String> eventIds);
}
