package co.cobre.notifications.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a single delivery attempt of a notification.
 */
public record DeliveryAttempt(
    UUID id,
    EventId eventId,
    int cycle,
    int attemptNumber,
    Instant nextAttemptAt,
    Optional<Instant> claimedAt,
    Optional<String> claimedBy,
    Optional<Instant> executedAt,
    Optional<Integer> responseStatus,
    Optional<String> failureReason,
    Optional<Duration> latency,
    AttemptOrigin origin
) {

    /**
     * Creates the first delivery attempt for an event.
     */
    public static DeliveryAttempt first(EventId eventId, int cycle, Instant at, AttemptOrigin origin) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Creates the next delivery attempt based on this one.
     */
    public DeliveryAttempt next(Instant at) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Checks if this attempt has been executed.
     */
    public boolean isExecuted() {
        throw new UnsupportedOperationException("not implemented");
    }
}
