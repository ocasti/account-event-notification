package co.cobre.notifications.infrastructure.persistence.jpa;

import co.cobre.notifications.infrastructure.persistence.entity.DeliveryStatusEntity;
import co.cobre.notifications.infrastructure.persistence.entity.NotificationEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEventEntity, String> {

    Optional<NotificationEventEntity> findByEventIdAndClientId(String eventId, String clientId);

    @Query("SELECT ne FROM NotificationEventEntity ne " +
           "WHERE ne.clientId = :clientId " +
           "AND (:status IS NULL OR ne.status = :status) " +
           "AND (:from IS NULL OR ne.createdAt >= :from) " +
           "AND (:to IS NULL OR ne.createdAt <= :to) " +
           "AND ((:cursorCreatedAt IS NULL OR :cursorEventId IS NULL) OR " +
           "     (ne.createdAt < :cursorCreatedAt OR " +
           "      (ne.createdAt = :cursorCreatedAt AND ne.eventId < :cursorEventId))) " +
           "ORDER BY ne.createdAt DESC, ne.eventId DESC")
    Page<NotificationEventEntity> searchEvents(
        @Param("clientId") String clientId,
        @Param("status") Optional<DeliveryStatusEntity> status,
        @Param("from") Optional<Instant> from,
        @Param("to") Optional<Instant> to,
        @Param("cursorCreatedAt") Optional<Instant> cursorCreatedAt,
        @Param("cursorEventId") Optional<String> cursorEventId,
        Pageable pageable
    );

    @Modifying
    @Query("UPDATE NotificationEventEntity ne " +
           "SET ne.status = :newStatus, ne.cycle = :cycle, ne.deliveredAt = :deliveredAt " +
           "WHERE ne.eventId = :eventId AND ne.status = :expectedStatus")
    int updateStatusIfMatches(
        @Param("eventId") String eventId,
        @Param("expectedStatus") DeliveryStatusEntity expectedStatus,
        @Param("newStatus") DeliveryStatusEntity newStatus,
        @Param("cycle") int cycle,
        @Param("deliveredAt") Instant deliveredAt
    );
}
