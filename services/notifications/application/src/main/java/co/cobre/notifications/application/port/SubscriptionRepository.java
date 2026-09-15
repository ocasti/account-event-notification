package co.cobre.notifications.application.port;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.Subscription;

import java.util.Optional;

/**
 * Port for retrieving subscription information.
 */
public interface SubscriptionRepository {

    /**
     * Finds an active subscription for a client and event key.
     */
    Optional<Subscription> findActive(ClientId clientId, EventKey eventKey);

    /**
     * Finds a subscription by ID.
     */
    Optional<Subscription> findById(String subscriptionId);
}
