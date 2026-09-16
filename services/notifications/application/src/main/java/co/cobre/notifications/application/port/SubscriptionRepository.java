package co.cobre.notifications.application.port;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.Subscription;

import java.util.Optional;

public interface SubscriptionRepository {

    Optional<Subscription> findActive(ClientId clientId, EventKey eventKey);

    Optional<Subscription> findById(String subscriptionId);
}
