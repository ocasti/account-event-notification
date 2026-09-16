package co.cobre.notifications.domain;

import java.time.Duration;
import java.util.Optional;

public sealed interface DeliveryOutcome {

    boolean isRetryable();

    record Success(int responseStatus, Duration latency) implements DeliveryOutcome {
        @Override
        public boolean isRetryable() {
            return false;
        }
    }

    record TransientFailure(Optional<Integer> responseStatus, String reason, Duration latency) implements DeliveryOutcome {
        @Override
        public boolean isRetryable() {
            return true;
        }
    }

    record PermanentFailure(int responseStatus, String reason, Duration latency) implements DeliveryOutcome {
        @Override
        public boolean isRetryable() {
            return false;
        }
    }
}
