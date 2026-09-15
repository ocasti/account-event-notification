package co.cobre.notifications.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a subscription to receive notifications.
 */
public record Subscription(
    String id,
    String clientId,
    Set<String> eventKeys,
    URI url,
    Optional<String> description,
    Optional<String> signatureKey,
    boolean active,
    Instant createdAt
) {
    public static final String ALL_EVENTS = "*";

    /**
     * Checks if this subscription matches the given event key.
     */
    public boolean matches(String eventKey) {
        throw new UnsupportedOperationException("not implemented");
    }
}
