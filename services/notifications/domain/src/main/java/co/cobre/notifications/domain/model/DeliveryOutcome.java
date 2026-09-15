package co.cobre.notifications.domain.model;

import java.time.Duration;
import java.util.Optional;

/**
 * Sealed interface representing the outcome of a delivery attempt.
 */
public sealed interface DeliveryOutcome {

    /**
     * Indicates whether the delivery is retryable.
     */
    boolean isRetryable();

    /**
     * A successful delivery outcome.
     */
    record Success(int responseStatus, Duration latency) implements DeliveryOutcome {
        @Override
        public boolean isRetryable() {
            return false;
        }
    }

    /**
     * A transient failure that may be retried.
     */
    record TransientFailure(Optional<Integer> responseStatus, String reason, Duration latency) implements DeliveryOutcome {
        @Override
        public boolean isRetryable() {
            return true;
        }
    }

    /**
     * A permanent failure that should not be retried.
     */
    record PermanentFailure(int responseStatus, String reason, Duration latency) implements DeliveryOutcome {
        @Override
        public boolean isRetryable() {
            return false;
        }
    }
}
