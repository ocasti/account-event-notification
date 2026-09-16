package co.cobre.notifications.application.usecase;

import java.time.Duration;
import java.util.Objects;

public record DeliveryWorkerSettings(
    String workerId,
    int batchSize,
    int maxPerClient,
    Duration lease
) {
    public DeliveryWorkerSettings {
        Objects.requireNonNull(workerId);
        Objects.requireNonNull(lease);
        if (workerId.isBlank()) throw new IllegalArgumentException("workerId cannot be blank");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
        if (maxPerClient <= 0) throw new IllegalArgumentException("maxPerClient must be positive");
        if (lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("lease must be positive");
    }
}
