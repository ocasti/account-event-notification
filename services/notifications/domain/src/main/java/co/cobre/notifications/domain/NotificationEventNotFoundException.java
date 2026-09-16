package co.cobre.notifications.domain;

public class NotificationEventNotFoundException extends RuntimeException {

    public NotificationEventNotFoundException(EventId eventId) {
        super(String.format("Notification event not found: %s", eventId.value()));
    }
}
