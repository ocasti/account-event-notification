package co.cobre.notifications.domain;

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
        return new DeliveryAttempt(
            UUID.randomUUID(),
            eventId,
            cycle,
            1,
            at,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            origin
        );
    }

    /**
     * Creates the next delivery attempt based on this one.
     */
    public DeliveryAttempt next(Instant at) {
        return new DeliveryAttempt(
            UUID.randomUUID(),
            eventId,
            cycle,
            attemptNumber + 1,
            at,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            AttemptOrigin.SYSTEM
        );
    }

    /**
     * Checks if this attempt has been executed.
     */
    public boolean isExecuted() {
        return executedAt.isPresent();
    }

    /**
     * Returns a new attempt marked as executed with the given result.
     */
    public DeliveryAttempt executed(Instant at, String workerId, DeliveryResult result) {
        return new DeliveryAttempt(
            id,
            eventId,
            cycle,
            attemptNumber,
            nextAttemptAt,
            claimedAt,
            Optional.of(workerId),
            Optional.of(at),
            result.responseStatus(),
            result.failureReason(),
            result.latency(),
            origin
        );
    }
}
