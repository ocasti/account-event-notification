package co.cobre.notifications.infrastructure.persistence;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEventEntity, String>, JpaSpecificationExecutor<NotificationEventEntity> {

    Optional<NotificationEventEntity> findByEventIdAndClientId(String eventId, String clientId);

    default Page<NotificationEventEntity> search(SearchCriteria criteria) {
        Specification<NotificationEventEntity> spec = (root, query, cb) -> {
            query.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("eventId")));
            Predicate[] predicates = Stream.of(
                    Optional.of(cb.equal(root.get("clientId"), criteria.clientId())),
                    criteria.status().map(status -> cb.equal(root.get("status"), status)),
                    criteria.from().map(from -> cb.greaterThanOrEqualTo(root.get("createdAt"), from)),
                    criteria.to().map(to -> cb.lessThanOrEqualTo(root.get("createdAt"), to)),
                    keysetAfterCursor(criteria, root, cb))
                .flatMap(Optional::stream)
                .toArray(Predicate[]::new);
            return cb.and(predicates);
        };
        Pageable pageable = PageRequest.of(0, criteria.limit() + 1);

        return findAll(spec, pageable);
    }

    private static Optional<Predicate> keysetAfterCursor(
        SearchCriteria criteria, Root<NotificationEventEntity> root, CriteriaBuilder cb
    ) {
        return criteria.cursorCreatedAt().flatMap(createdAt -> criteria.cursorEventId().map(eventId -> cb.or(
            cb.lessThan(root.get("createdAt"), createdAt),
            cb.and(cb.equal(root.get("createdAt"), createdAt), cb.lessThan(root.get("eventId"), eventId))
        )));
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
