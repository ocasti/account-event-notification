package co.cobre.notifications.infrastructure.fixtures;

import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptEntity;
import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptEntityMapper;
import co.cobre.notifications.infrastructure.persistence.NotificationEventEntity;
import co.cobre.notifications.infrastructure.persistence.NotificationEventEntityMapper;
import co.cobre.notifications.infrastructure.persistence.SubscriptionEntity;
import co.cobre.notifications.infrastructure.persistence.SubscriptionEntityMapper;

/**
 * Builds JPA entities from the domain objects produced by
 * {@code co.cobre.notifications.domain.fixtures}, going through the real mappers rather than
 * setter calls, so persistence tests seed rows without duplicating entity construction.
 */
public final class Entities {

    private static final NotificationEventEntityMapper EVENT_MAPPER = new NotificationEventEntityMapper();
    private static final DeliveryAttemptEntityMapper ATTEMPT_MAPPER = new DeliveryAttemptEntityMapper();
    private static final SubscriptionEntityMapper SUBSCRIPTION_MAPPER = new SubscriptionEntityMapper();

    private Entities() {
    }

    public static NotificationEventEntity notificationEvent(NotificationEvent domain) {
        return EVENT_MAPPER.toEntity(domain);
    }

    public static DeliveryAttemptEntity deliveryAttempt(DeliveryAttempt domain) {
        return ATTEMPT_MAPPER.toEntity(domain);
    }

    public static SubscriptionEntity subscription(Subscription domain) {
        return SUBSCRIPTION_MAPPER.toEntity(domain);
    }
}
