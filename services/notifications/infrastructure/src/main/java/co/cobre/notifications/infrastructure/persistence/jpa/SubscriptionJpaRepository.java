package co.cobre.notifications.infrastructure.persistence.jpa;

import co.cobre.notifications.infrastructure.persistence.entity.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for subscriptions.
 */
public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, String> {
}
