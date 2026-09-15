package co.cobre.notifications.domain.exception;

/**
 * Raised when a notification event is not found.
 */
public class NotificationEventNotFoundException extends RuntimeException {

    /**
     * Creates an exception when a notification event is not found.
     */
    public NotificationEventNotFoundException(String eventId) {
        super(String.format("Notification event not found: %s", eventId));
    }
}
