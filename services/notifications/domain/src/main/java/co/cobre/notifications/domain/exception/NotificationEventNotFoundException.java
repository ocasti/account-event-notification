package co.cobre.notifications.domain.exception;

import co.cobre.notifications.domain.model.EventId;

/**
 * Raised when a notification event is not found.
 */
public class NotificationEventNotFoundException extends RuntimeException {

    /**
     * Creates an exception when a notification event is not found.
     */
    public NotificationEventNotFoundException(EventId eventId) {
        super(String.format("Notification event not found: %s", eventId.value()));
    }
}
