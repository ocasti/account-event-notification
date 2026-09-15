package co.cobre.notifications.domain.model;

import java.time.Duration;
import java.util.Optional;

/** Record representing the result of a delivery attempt. */
public record DeliveryResult(
    Optional<Integer> responseStatus,
    Optional<String> failureReason,
    Optional<Duration> latency
) {
    public static DeliveryResult of(DeliveryOutcome outcome) {
        return switch (outcome) {
            case DeliveryOutcome.Success s -> new DeliveryResult(
                Optional.of(s.responseStatus()),
                Optional.empty(),
                Optional.of(s.latency())
            );
            case DeliveryOutcome.TransientFailure tf -> new DeliveryResult(
                tf.responseStatus(),
                Optional.of(tf.reason()),
                Optional.of(tf.latency())
            );
            case DeliveryOutcome.PermanentFailure pf -> new DeliveryResult(
                Optional.of(pf.responseStatus()),
                Optional.of(pf.reason()),
                Optional.of(pf.latency())
            );
        };
    }
}
