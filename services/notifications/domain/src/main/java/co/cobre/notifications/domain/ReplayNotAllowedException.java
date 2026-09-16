package co.cobre.notifications.domain;

import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;

public class ReplayNotAllowedException extends RuntimeException {

    public ReplayNotAllowedException(EventId eventId, DeliveryStatus current) {
        super(String.format("Replay not allowed for event %s in status %s", eventId.value(), current));
    }
}
