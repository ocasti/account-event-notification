package co.cobre.notifications.domain.fixtures;

import co.cobre.notifications.domain.DeliveryOutcome;

import java.time.Duration;
import java.util.Optional;

/**
 * Object Mother for {@link DeliveryOutcome}.
 */
public final class DeliveryOutcomes {

    private static final Duration LATENCY = Duration.ofMillis(120);

    private DeliveryOutcomes() {
    }

    public static DeliveryOutcome.Success success() {
        return new DeliveryOutcome.Success(200, LATENCY);
    }

    public static DeliveryOutcome.TransientFailure transientFailure(int status) {
        return new DeliveryOutcome.TransientFailure(Optional.of(status), "service unavailable", LATENCY);
    }

    public static DeliveryOutcome.TransientFailure timeout() {
        return new DeliveryOutcome.TransientFailure(Optional.empty(), "timeout", Duration.ofSeconds(5));
    }

    public static DeliveryOutcome.PermanentFailure permanentFailure(int status) {
        return new DeliveryOutcome.PermanentFailure(status, "permanent failure", LATENCY);
    }
}
