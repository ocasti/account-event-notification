package co.cobre.notifications.infrastructure.persistence.mapper;

import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.domain.model.EventKey;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.infrastructure.persistence.entity.DeliveryStatusEntity;
import co.cobre.notifications.infrastructure.persistence.entity.NotificationEventEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NotificationEventEntityMapper {

    public NotificationEvent toDomain(NotificationEventEntity entity) {
        return new NotificationEvent(
            new EventId(entity.getEventId()),
            new ClientId(entity.getClientId()),
            new EventKey(entity.getEventKey()),
            entity.getContent(),
            entity.getCreatedAt(),
            entity.getReceivedAt(),
            mapStatusToDomain(entity.getStatus()),
            Optional.ofNullable(entity.getSubscriptionId()),
            entity.getCycle(),
            Optional.ofNullable(entity.getDeliveredAt())
        );
    }

    public NotificationEventEntity toEntity(NotificationEvent domain) {
        var entity = new NotificationEventEntity();
        entity.setEventId(domain.eventId().value());
        entity.setClientId(domain.clientId().value());
        entity.setEventKey(domain.eventKey().value());
        entity.setContent(domain.content());
        entity.setCreatedAt(domain.createdAt());
        entity.setReceivedAt(domain.receivedAt());
        entity.setStatus(mapStatusToEntity(domain.status()));
        entity.setSubscriptionId(domain.subscriptionId().orElse(null));
        entity.setCycle(domain.cycle());
        entity.setDeliveredAt(domain.deliveredAt().orElse(null));
        return entity;
    }

    private DeliveryStatus mapStatusToDomain(DeliveryStatusEntity entity) {
        return DeliveryStatus.valueOf(entity.name());
    }

    private DeliveryStatusEntity mapStatusToEntity(DeliveryStatus domain) {
        return DeliveryStatusEntity.valueOf(domain.name());
    }
}
