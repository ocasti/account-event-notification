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
        requireWorkerId(workerId);
        requirePositiveBatchSize(batchSize);
        requirePositiveMaxPerClient(maxPerClient);
        requirePositiveLease(lease);
    }

    private static void requireWorkerId(String workerId) {
        Objects.requireNonNull(workerId);
        if (workerId.isBlank()) throw new IllegalArgumentException("workerId cannot be blank");
    }

    private static void requirePositiveBatchSize(int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
    }

    private static void requirePositiveMaxPerClient(int maxPerClient) {
        if (maxPerClient <= 0) throw new IllegalArgumentException("maxPerClient must be positive");
    }

    private static void requirePositiveLease(Duration lease) {
        Objects.requireNonNull(lease);
        if (lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("lease must be positive");
    }
}
