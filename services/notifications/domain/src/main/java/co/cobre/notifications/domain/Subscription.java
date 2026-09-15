package co.cobre.notifications.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a subscription to receive notifications.
 */
public record Subscription(
    String id,
    ClientId clientId,
    Set<EventKey> eventKeys,
    WebhookUrl url,
    Optional<String> description,
    Optional<String> signatureKey,
    boolean active,
    Instant createdAt
) {

    /**
     * Checks if this subscription matches the given event key.
     */
    public boolean matches(EventKey eventKey) {
        if (!active) {
            return false;
        }
        return eventKeys.contains(eventKey) || eventKeys.contains(EventKey.wildcard());
    }
}
