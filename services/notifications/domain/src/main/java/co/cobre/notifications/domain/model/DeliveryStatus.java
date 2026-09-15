package co.cobre.notifications.domain.model;

/**
 * Represents the delivery status of a notification.
 */
public enum DeliveryStatus {
    PENDING,
    RETRYING,
    COMPLETED,
    FAILED,
    SKIPPED;

    /**
     * Determines if a transition to the target status is allowed.
     */
    public boolean canTransitionTo(DeliveryStatus target) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns true if this status is terminal (no further transitions allowed).
     */
    public boolean isTerminal() {
        throw new UnsupportedOperationException("not implemented");
    }
}
