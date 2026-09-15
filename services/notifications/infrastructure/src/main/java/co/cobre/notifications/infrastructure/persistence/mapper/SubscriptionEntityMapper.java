package co.cobre.notifications.infrastructure.persistence.mapper;

import co.cobre.notifications.domain.model.Subscription;
import co.cobre.notifications.infrastructure.persistence.entity.SubscriptionEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper for subscription entities.
 */
@Component
public class SubscriptionEntityMapper {

    /**
     * Maps entity to domain model.
     */
    public Subscription toDomain(SubscriptionEntity entity) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Maps domain model to entity.
     */
    public SubscriptionEntity toEntity(Subscription domain) {
        throw new UnsupportedOperationException("not implemented");
    }
}
