package co.cobre.notifications.application.port;

import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventId;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface DeliveryAttemptRepository {

    void save(DeliveryAttempt attempt);

    List<DeliveryAttempt> claimDue(DeliveryClaim claim);

    boolean recordResultIf(DeliveryAttempt executed, String claimedBy);

    List<DeliveryAttempt> findByEvent(EventId eventId);

    /**
     * Returns a map with only the event IDs that have attempts.
     * Empty collection returns empty map without querying the database.
     */
    Map<EventId, Integer> countByEvents(Collection<EventId> eventIds);
}
