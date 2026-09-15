package co.cobre.notifications.infrastructure.persistence.jpa;

import co.cobre.notifications.infrastructure.persistence.entity.DeliveryStatusEntity;
import co.cobre.notifications.infrastructure.persistence.entity.NotificationEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEventEntity, String>, JpaSpecificationExecutor<NotificationEventEntity> {

    Optional<NotificationEventEntity> findByEventIdAndClientId(String eventId, String clientId);

    default Page<NotificationEventEntity> searchEvents(
        String clientId,
        Optional<DeliveryStatusEntity> status,
        Optional<Instant> from,
        Optional<Instant> to,
        Optional<Instant> cursorCreatedAt,
        Optional<String> cursorEventId,
        int limit
    ) {
        Specification<NotificationEventEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            // Always filter by clientId
            predicates.add(cb.equal(root.get("clientId"), clientId));

            // Optional status filter
            if (status.isPresent()) {
                predicates.add(cb.equal(root.get("status"), status.get()));
            }

            // Optional from date filter
            if (from.isPresent()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.get()));
            }

            // Optional to date filter
            if (to.isPresent()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to.get()));
            }

            // Keyset pagination filter
            if (cursorCreatedAt.isPresent() && cursorEventId.isPresent()) {
                jakarta.persistence.criteria.Predicate keysetPredicate = cb.or(
                    cb.lessThan(root.get("createdAt"), cursorCreatedAt.get()),
                    cb.and(
                        cb.equal(root.get("createdAt"), cursorCreatedAt.get()),
                        cb.lessThan(root.get("eventId"), cursorEventId.get())
                    )
                );
                predicates.add(keysetPredicate);
            }

            // Order by createdAt DESC, eventId DESC
            query.orderBy(
                cb.desc(root.get("createdAt")),
                cb.desc(root.get("eventId"))
            );

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Pageable pageable = PageRequest.of(0, limit + 1);
        return findAll(spec, pageable);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
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
