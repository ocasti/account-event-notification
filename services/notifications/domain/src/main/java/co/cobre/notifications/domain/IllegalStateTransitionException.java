package co.cobre.notifications.domain;

import co.cobre.notifications.domain.DeliveryStatus;

/**
 * Raised when an illegal state transition is attempted.
 */
public class IllegalStateTransitionException extends RuntimeException {
    private final DeliveryStatus from;
    private final DeliveryStatus to;

    /**
     * Creates an exception for an illegal transition.
     */
    public IllegalStateTransitionException(DeliveryStatus from, DeliveryStatus to) {
        super(String.format("Illegal transition from %s to %s", from, to));
        this.from = from;
        this.to = to;
    }

    /**
     * Returns the source status.
     */
    public DeliveryStatus from() {
        return from;
    }

    /**
     * Returns the target status.
     */
    public DeliveryStatus to() {
        return to;
    }
}
