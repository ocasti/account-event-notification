package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.UseCase;
import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.usecase.NotificationEventDetail;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;

/**
 * Use case for retrieving a notification event with its delivery attempts.
 */
@UseCase
public final class GetNotificationEvent {
    private final NotificationEventRepository events;
    private final DeliveryAttemptRepository attempts;

    /**
     * Creates a new get notification event use case.
     */
    public GetNotificationEvent(
        NotificationEventRepository events,
        DeliveryAttemptRepository attempts
    ) {
        this.events = events;
        this.attempts = attempts;
    }

    /**
     * Gets a notification event by client and event ID.
     */
    public NotificationEventDetail get(ClientId clientId, EventId eventId) {
        var event = events.findByClientAndId(clientId, eventId)
            .orElseThrow(() -> new NotificationEventNotFoundException(eventId));
        var eventAttempts = attempts.findByEvent(eventId);
        return new NotificationEventDetail(event, eventAttempts);
    }
}
