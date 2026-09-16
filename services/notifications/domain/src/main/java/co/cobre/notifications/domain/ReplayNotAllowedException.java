package co.cobre.notifications.domain;

public class ReplayNotAllowedException extends RuntimeException {

    public ReplayNotAllowedException(EventId eventId, DeliveryStatus current) {
        super(String.format("Replay not allowed for event %s in status %s", eventId.value(), current));
    }
}
