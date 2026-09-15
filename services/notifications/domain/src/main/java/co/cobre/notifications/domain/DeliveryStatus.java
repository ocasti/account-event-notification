package co.cobre.notifications.domain;

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
        return switch (this) {
            case PENDING -> target == COMPLETED || target == RETRYING || target == FAILED;
            case RETRYING -> target == COMPLETED || target == RETRYING || target == FAILED;
            case FAILED -> target == PENDING;
            case COMPLETED, SKIPPED -> false;
        };
    }

    /**
     * Returns true if this status is terminal (no further transitions allowed).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == SKIPPED;
    }
}
