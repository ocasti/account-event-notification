package co.cobre.notifications.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

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

    public boolean matches(EventKey eventKey) {
        if (!active) {
            return false;
        }
        return eventKeys.contains(eventKey) || eventKeys.contains(EventKey.wildcard());
    }
}
