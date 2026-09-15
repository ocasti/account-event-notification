package co.cobre.notifications.infrastructure.persistence.jpa;

import co.cobre.notifications.infrastructure.persistence.entity.DeliveryAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * JPA repository for delivery attempts.
 */
public interface DeliveryAttemptJpaRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {
}
