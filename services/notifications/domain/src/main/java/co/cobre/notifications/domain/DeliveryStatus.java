package co.cobre.notifications.domain;

import java.util.Optional;

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

    /**
     * Looks up a status by its API value (case-insensitive), without relying on
     * the exception-based lookup of {@link #valueOf(String)}.
     */
    public static Optional<DeliveryStatus> fromApiValue(String value) {
        var upper = value.toUpperCase();
        for (var status : values()) {
            if (status.name().equals(upper)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
