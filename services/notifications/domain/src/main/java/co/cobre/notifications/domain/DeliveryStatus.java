package co.cobre.notifications.domain;

public enum DeliveryStatus {
    PENDING,
    RETRYING,
    COMPLETED,
    FAILED,
    SKIPPED;

    public boolean canTransitionTo(DeliveryStatus target) {
        return switch (this) {
            case PENDING -> target == COMPLETED || target == RETRYING || target == FAILED;
            case RETRYING -> target == COMPLETED || target == RETRYING || target == FAILED;
            case FAILED -> target == PENDING;
            case COMPLETED, SKIPPED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == SKIPPED;
    }
}
