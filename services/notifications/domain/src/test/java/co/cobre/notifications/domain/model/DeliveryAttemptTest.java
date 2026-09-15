package co.cobre.notifications.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAttemptTest {

    private final EventId eventId = new EventId("event-1");
    private final Instant now = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void shouldCreateFirstAttemptWithAttemptNumberOne() {
        var attempt = DeliveryAttempt.first(eventId, 0, now, AttemptOrigin.SYSTEM);

        assertThat(attempt.attemptNumber()).isOne();
        assertThat(attempt.cycle()).isZero();
        assertThat(attempt.nextAttemptAt()).isEqualTo(now);
        assertThat(attempt.claimedAt()).isEmpty();
        assertThat(attempt.claimedBy()).isEmpty();
        assertThat(attempt.executedAt()).isEmpty();
        assertThat(attempt.responseStatus()).isEmpty();
        assertThat(attempt.failureReason()).isEmpty();
        assertThat(attempt.latency()).isEmpty();
        assertThat(attempt.origin()).isEqualTo(AttemptOrigin.SYSTEM);
    }

    @Test
    void shouldCreateFirstAttemptWithEventIdAndCycle() {
        var attempt = DeliveryAttempt.first(eventId, 2, now, AttemptOrigin.SYSTEM);

        assertThat(attempt.eventId()).isEqualTo(eventId);
        assertThat(attempt.cycle()).isEqualTo(2);
    }

    @Test
    void shouldCreateNextAttemptWithIncrementedAttemptNumber() {
        var first = DeliveryAttempt.first(eventId, 0, now, AttemptOrigin.SYSTEM);
        var nextInstant = now.plusSeconds(30);

        var next = first.next(nextInstant);

        assertThat(next.attemptNumber()).isEqualTo(first.attemptNumber() + 1);
        assertThat(next.cycle()).isEqualTo(first.cycle());
        assertThat(next.eventId()).isEqualTo(first.eventId());
        assertThat(next.nextAttemptAt()).isEqualTo(nextInstant);
        assertThat(next.origin()).isEqualTo(AttemptOrigin.SYSTEM);
    }

    @Test
    void shouldCreateNextAttemptWithDistinctId() {
        var first = DeliveryAttempt.first(eventId, 0, now, AttemptOrigin.SYSTEM);
        var nextInstant = now.plusSeconds(30);

        var next = first.next(nextInstant);

        assertThat(next.id()).isNotEqualTo(first.id());
    }

    @Test
    void shouldReportNotExecutedWhenExecutedAtEmpty() {
        var attempt = DeliveryAttempt.first(eventId, 0, now, AttemptOrigin.SYSTEM);

        assertThat(attempt.isExecuted()).isFalse();
    }

    @Test
    void shouldReportExecutedWhenExecutedAtPresent() {
        var attempt = DeliveryAttempt.first(eventId, 0, now, AttemptOrigin.SYSTEM);
        var executedAtInstant = now.plusSeconds(5);

        var executed = new DeliveryAttempt(
            attempt.id(),
            attempt.eventId(),
            attempt.cycle(),
            attempt.attemptNumber(),
            attempt.nextAttemptAt(),
            attempt.claimedAt(),
            attempt.claimedBy(),
            java.util.Optional.of(executedAtInstant),
            attempt.responseStatus(),
            attempt.failureReason(),
            attempt.latency(),
            attempt.origin()
        );

        assertThat(executed.isExecuted()).isTrue();
    }

    @Test
    void shouldExecuteAttemptWithResult() {
        var attempt = DeliveryAttempt.first(eventId, 0, now, AttemptOrigin.SYSTEM);
        var result = new DeliveryResult(
            java.util.Optional.of(200),
            java.util.Optional.empty(),
            java.util.Optional.of(java.time.Duration.ofMillis(100))
        );
        var executedAtInstant = now.plusSeconds(5);

        var executed = attempt.executed(executedAtInstant, "worker-1", result);

        assertThat(executed.id()).isEqualTo(attempt.id());
        assertThat(executed.executedAt()).contains(executedAtInstant);
        assertThat(executed.claimedBy()).contains("worker-1");
        assertThat(executed.responseStatus()).contains(200);
        assertThat(executed.failureReason()).isEmpty();
        assertThat(executed.latency()).contains(java.time.Duration.ofMillis(100));
    }
}
