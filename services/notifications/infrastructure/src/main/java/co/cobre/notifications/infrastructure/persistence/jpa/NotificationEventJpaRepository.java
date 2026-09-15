package co.cobre.notifications.infrastructure.persistence.jpa;

import co.cobre.notifications.infrastructure.persistence.entity.NotificationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for notification events.
 */
public interface NotificationEventJpaRepository extends JpaRepository<NotificationEventEntity, String> {
}
