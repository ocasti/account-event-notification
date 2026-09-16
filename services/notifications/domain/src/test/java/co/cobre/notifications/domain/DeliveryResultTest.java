package co.cobre.notifications.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryResultTest {

    @Test
    void createsFromSuccess() {
        var outcome = new DeliveryOutcome.Success(200, Duration.ofMillis(100));
        var result = DeliveryResult.of(outcome);

        assertEquals(Optional.of(200), result.responseStatus());
        assertTrue(result.failureReason().isEmpty());
        assertEquals(Optional.of(Duration.ofMillis(100)), result.latency());
        assertFalse(outcome.isRetryable());
    }

    @Test
    void createsFromTransientFailure() {
        var outcome = new DeliveryOutcome.TransientFailure(
            Optional.of(503),
            "service unavailable",
            Duration.ofMillis(50)
        );
        var result = DeliveryResult.of(outcome);

        assertEquals(Optional.of(503), result.responseStatus());
        assertEquals(Optional.of("service unavailable"), result.failureReason());
        assertEquals(Optional.of(Duration.ofMillis(50)), result.latency());
        assertTrue(outcome.isRetryable());
    }

    @Test
    void createsFromPermanentFailure() {
        var outcome = new DeliveryOutcome.PermanentFailure(
            410,
            "gone",
            Duration.ofMillis(75)
        );
        var result = DeliveryResult.of(outcome);

        assertEquals(Optional.of(410), result.responseStatus());
        assertEquals(Optional.of("gone"), result.failureReason());
        assertEquals(Optional.of(Duration.ofMillis(75)), result.latency());
        assertFalse(outcome.isRetryable());
    }
}
