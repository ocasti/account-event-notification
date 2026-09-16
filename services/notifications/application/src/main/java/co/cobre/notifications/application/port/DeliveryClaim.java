package co.cobre.notifications.application.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record DeliveryClaim(
    Instant now,
    int limit,
    int maxPerClient,
    String workerId,
    Duration lease
) {
    public DeliveryClaim {
        requireNow(now);
        requirePositiveLimit(limit);
        requirePositiveMaxPerClient(maxPerClient);
        requireWorkerId(workerId);
        requirePositiveLease(lease);
    }

    private static void requireNow(Instant now) {
        Objects.requireNonNull(now);
    }

    private static void requirePositiveLimit(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
    }

    private static void requirePositiveMaxPerClient(int maxPerClient) {
        if (maxPerClient <= 0) throw new IllegalArgumentException("maxPerClient must be positive");
    }

    private static void requireWorkerId(String workerId) {
        Objects.requireNonNull(workerId);
        if (workerId.isBlank()) throw new IllegalArgumentException("workerId cannot be blank");
    }

    private static void requirePositiveLease(Duration lease) {
        Objects.requireNonNull(lease);
        if (lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("lease must be positive");
    }
}
