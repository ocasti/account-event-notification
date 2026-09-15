package co.cobre.notifications.infrastructure.persistence.mapper;

import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.EventKey;
import co.cobre.notifications.domain.model.Subscription;
import co.cobre.notifications.domain.model.WebhookUrl;
import co.cobre.notifications.infrastructure.persistence.entity.SubscriptionEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class SubscriptionEntityMapper {

    public Subscription toDomain(SubscriptionEntity entity) {
        return new Subscription(
            entity.getId(),
            new ClientId(entity.getClientId()),
            convertArrayToSet(entity.getEventKeys()),
            WebhookUrl.of(entity.getUrl()),
            Optional.ofNullable(entity.getDescription()),
            Optional.ofNullable(entity.getEventSignatureKey()),
            entity.isActive(),
            entity.getCreatedAt()
        );
    }

    public SubscriptionEntity toEntity(Subscription domain) {
        var entity = new SubscriptionEntity();
        entity.setId(domain.id());
        entity.setClientId(domain.clientId().value());
        entity.setEventKeys(convertSetToArray(domain.eventKeys()));
        entity.setUrl(domain.url().value().toString());
        entity.setDescription(domain.description().orElse(null));
        entity.setEventSignatureKey(domain.signatureKey().orElse(null));
        entity.setActive(domain.active());
        entity.setCreatedAt(domain.createdAt());
        return entity;
    }

    private Set<EventKey> convertArrayToSet(String[] array) {
        return java.util.Arrays.stream(array)
            .map(EventKey::new)
            .collect(java.util.stream.Collectors.toSet());
    }

    private String[] convertSetToArray(Set<EventKey> set) {
        return set.stream()
            .map(EventKey::value)
            .toArray(String[]::new);
    }
}
