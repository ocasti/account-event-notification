package co.cobre.notifications.domain;

import co.cobre.notifications.domain.EventId;

public class NotificationEventNotFoundException extends RuntimeException {

    public NotificationEventNotFoundException(EventId eventId) {
        super(String.format("Notification event not found: %s", eventId.value()));
    }
}
