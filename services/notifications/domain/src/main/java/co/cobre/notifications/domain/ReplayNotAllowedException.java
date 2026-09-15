package co.cobre.notifications.domain;

import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;

/**
 * Raised when replay is attempted on an event that does not allow it.
 */
public class ReplayNotAllowedException extends RuntimeException {

    /**
     * Creates an exception when replay is not allowed for an event.
     */
    public ReplayNotAllowedException(EventId eventId, DeliveryStatus current) {
        super(String.format("Replay not allowed for event %s in status %s", eventId.value(), current));
    }
}
