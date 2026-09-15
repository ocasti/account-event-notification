package co.cobre.notifications.application.port;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEventsQuery;
import co.cobre.notifications.application.usecase.NotificationEventPage;

import java.util.Optional;

/**
 * Port for persisting and retrieving notification events.
 */
public interface NotificationEventRepository {

    /**
     * Saves a notification event.
     */
    void save(NotificationEvent event);

    /**
     * Finds an event by client and event ID.
     */
    Optional<NotificationEvent> findByClientAndId(ClientId clientId, EventId eventId);

    /**
     * Finds an event by ID.
     */
    Optional<NotificationEvent> findById(EventId eventId);

    /**
     * Checks if an event exists by ID.
     */
    boolean existsById(EventId eventId);

    /**
     * Searches for events based on the query criteria.
     */
    NotificationEventPage search(ListNotificationEventsQuery query);

    /**
     * Transitions an event to a new status if the current status matches expected.
     */
    boolean transition(EventId eventId, DeliveryStatus expectedCurrent, NotificationEvent updated);
}
