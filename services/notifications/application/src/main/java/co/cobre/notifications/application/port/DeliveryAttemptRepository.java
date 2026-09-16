package co.cobre.notifications.application.port;

import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventId;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    /**
     * Counts delivery attempts by event IDs.
     * Returns a map with only the event IDs that have attempts.
     * Empty collection returns empty map without querying the database.
     */
    Map<EventId, Integer> countByEvents(Collection<EventId> eventIds);
}
