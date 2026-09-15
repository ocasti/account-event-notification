package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.infrastructure.persistence.DeliveryStatusEntity;
import co.cobre.notifications.infrastructure.persistence.NotificationEventEntity;
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

    default Page<NotificationEventEntity> search(SearchCriteria criteria) {
        Specification<NotificationEventEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("clientId"), criteria.clientId()));

            if (criteria.status().isPresent()) {
                predicates.add(cb.equal(root.get("status"), criteria.status().get()));
            }

            if (criteria.from().isPresent()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.from().get()));
            }

            if (criteria.to().isPresent()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), criteria.to().get()));
            }

            if (criteria.cursorCreatedAt().isPresent() && criteria.cursorEventId().isPresent()) {
                jakarta.persistence.criteria.Predicate keysetPredicate = cb.or(
                    cb.lessThan(root.get("createdAt"), criteria.cursorCreatedAt().get()),
                    cb.and(
                        cb.equal(root.get("createdAt"), criteria.cursorCreatedAt().get()),
                        cb.lessThan(root.get("eventId"), criteria.cursorEventId().get())
                    )
                );
                predicates.add(keysetPredicate);
            }

            query.orderBy(
                cb.desc(root.get("createdAt")),
                cb.desc(root.get("eventId"))
            );

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Pageable pageable = PageRequest.of(0, criteria.limit() + 1);
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
