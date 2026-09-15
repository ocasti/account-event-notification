package co.cobre.notifications.domain.policy;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * Policy that defines the retry strategy for failed deliveries.
 */
public record RetryPolicy(
    Duration baseDelay,
    double factor,
    Duration maxDelay,
    double jitterRatio,
    int maxAttempts
) {

    /**
     * Creates the standard retry policy with predefined values.
     */
    public static RetryPolicy standard() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Calculates the delay before the given attempt number.
     */
    public Duration delayBefore(int attemptNumber, RandomGenerator random) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Checks if the retry attempts have been exhausted.
     */
    public boolean isExhausted(int attemptNumber) {
        throw new UnsupportedOperationException("not implemented");
    }
}
