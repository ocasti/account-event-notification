package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.query.ListNotificationEventsQuery;
import co.cobre.notifications.application.query.NotificationEventPage;

/**
 * Use case for listing notification events.
 */
public final class ListNotificationEvents {
    private final NotificationEventRepository events;

    /**
     * Creates a new list notification events use case.
     */
    public ListNotificationEvents(NotificationEventRepository events) {
        this.events = events;
    }

    /**
     * Lists notification events.
     */
    public NotificationEventPage list(ListNotificationEventsQuery query) {
        return events.search(query);
    }
}
