package co.cobre.notifications.infrastructure.persistence.mapper;

import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.infrastructure.persistence.entity.DeliveryAttemptEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper for delivery attempt entities.
 */
@Component
public class DeliveryAttemptEntityMapper {

    /**
     * Maps entity to domain model.
     */
    public DeliveryAttempt toDomain(DeliveryAttemptEntity entity) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Maps domain model to entity.
     */
    public DeliveryAttemptEntity toEntity(DeliveryAttempt domain) {
        throw new UnsupportedOperationException("not implemented");
    }
}
