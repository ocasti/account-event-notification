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
        return new RetryPolicy(
            Duration.ofSeconds(30),
            4.0,
            Duration.ofMinutes(15),
            0.2,
            5
        );
    }

    /**
     * Calculates the delay before the given attempt number.
     */
    public Duration delayBefore(int attemptNumber, RandomGenerator random) {
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("Attempt number must be greater than 0");
        }
        if (attemptNumber == 1) {
            return Duration.ZERO;
        }

        long delayInSeconds = (long) (baseDelay.toSeconds() * Math.pow(factor, attemptNumber - 2));
        long maxDelayInSeconds = maxDelay.toSeconds();
        delayInSeconds = Math.min(delayInSeconds, maxDelayInSeconds);

        double jitterFactor = 1.0 + jitterRatio * (2.0 * random.nextDouble() - 1.0);
        long jitteredDelayInSeconds = (long) (delayInSeconds * jitterFactor);
        jitteredDelayInSeconds = Math.max(0, Math.min(jitteredDelayInSeconds, maxDelayInSeconds));

        return Duration.ofSeconds(jitteredDelayInSeconds);
    }

    /**
     * Checks if the retry attempts have been exhausted.
     */
    public boolean isExhausted(int attemptNumber) {
        return attemptNumber > maxAttempts;
    }
}
