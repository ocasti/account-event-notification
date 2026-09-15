package co.cobre.notifications.application.port;

import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Port for persisting and querying delivery attempts.
 */
public interface DeliveryAttemptRepository {

    /**
     * Saves a delivery attempt.
     */
    void save(DeliveryAttempt attempt);

    /**
     * Claims due delivery attempts for processing.
     */
    List<DeliveryAttempt> claimDue(DeliveryClaim claim);

    /**
     * Records the result of an executed delivery attempt if still claimed by the worker.
     */
    boolean recordResultIf(DeliveryAttempt executed, String claimedBy);

    /**
     * Finds all delivery attempts for an event.
     */
    List<DeliveryAttempt> findByEvent(EventId eventId);
}
