package co.cobre.notifications.application.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Record representing a claim for delivery attempts to be processed. */
public record DeliveryClaim(
    Instant now,
    int limit,
    int maxPerClient,
    String workerId,
    Duration lease
) {
    public DeliveryClaim {
        Objects.requireNonNull(now);
        Objects.requireNonNull(workerId);
        Objects.requireNonNull(lease);
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        if (maxPerClient <= 0) throw new IllegalArgumentException("maxPerClient must be positive");
        if (workerId.isBlank()) throw new IllegalArgumentException("workerId cannot be blank");
        if (lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("lease must be positive");
    }
}
