package co.cobre.notifications.domain;

import co.cobre.notifications.domain.DeliveryStatus;

public class IllegalStateTransitionException extends RuntimeException {
    private final DeliveryStatus from;
    private final DeliveryStatus to;

    public IllegalStateTransitionException(DeliveryStatus from, DeliveryStatus to) {
        super(String.format("Illegal transition from %s to %s", from, to));
        this.from = from;
        this.to = to;
    }

    public DeliveryStatus from() {
        return from;
    }

    public DeliveryStatus to() {
        return to;
    }
}
