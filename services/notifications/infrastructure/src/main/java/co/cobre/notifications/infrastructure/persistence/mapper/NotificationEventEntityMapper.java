package co.cobre.notifications.infrastructure.persistence.mapper;

import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.infrastructure.persistence.entity.NotificationEventEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper for notification event entities.
 */
@Component
public class NotificationEventEntityMapper {

    /**
     * Maps entity to domain model.
     */
    public NotificationEvent toDomain(NotificationEventEntity entity) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Maps domain model to entity.
     */
    public NotificationEventEntity toEntity(NotificationEvent domain) {
        throw new UnsupportedOperationException("not implemented");
    }
}
