package co.cobre.notifications.domain;

import java.time.Duration;

import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.DeliveryOutcomes;
import co.cobre.notifications.domain.fixtures.Ids;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAttemptTest {

    @Test
    void shouldSetAttemptNumberOneWhenCreatingFirstAttempt() {
        var attempt = DeliveryAttempt.first(Ids.EVT_001, 0, Clocks.NOW, AttemptOrigin.SYSTEM);

        assertThat(attempt.attemptNumber()).isOne();
        assertThat(attempt.cycle()).isZero();
        assertThat(attempt.nextAttemptAt()).isEqualTo(Clocks.NOW);
        assertThat(attempt.claimedAt()).isEmpty();
        assertThat(attempt.claimedBy()).isEmpty();
        assertThat(attempt.executedAt()).isEmpty();
        assertThat(attempt.responseStatus()).isEmpty();
        assertThat(attempt.failureReason()).isEmpty();
        assertThat(attempt.latency()).isEmpty();
        assertThat(attempt.origin()).isEqualTo(AttemptOrigin.SYSTEM);
    }

    @Test
    void shouldSetEventIdAndCycleWhenCreatingFirstAttempt() {
        var attempt = DeliveryAttempt.first(Ids.EVT_001, 2, Clocks.NOW, AttemptOrigin.SYSTEM);

        assertThat(attempt.eventId()).isEqualTo(Ids.EVT_001);
        assertThat(attempt.cycle()).isEqualTo(2);
    }

    @Test
    void shouldIncrementAttemptNumberWhenCreatingNextAttempt() {
        var first = DeliveryAttempt.first(Ids.EVT_001, 0, Clocks.NOW, AttemptOrigin.SYSTEM);
        var nextInstant = Clocks.NOW.plusSeconds(30);

        var next = first.next(nextInstant);

        assertThat(next.attemptNumber()).isEqualTo(first.attemptNumber() + 1);
        assertThat(next.cycle()).isEqualTo(first.cycle());
        assertThat(next.eventId()).isEqualTo(first.eventId());
        assertThat(next.nextAttemptAt()).isEqualTo(nextInstant);
        assertThat(next.origin()).isEqualTo(AttemptOrigin.SYSTEM);
    }

    @Test
    void shouldAssignDistinctIdWhenCreatingNextAttempt() {
        var first = DeliveryAttempt.first(Ids.EVT_001, 0, Clocks.NOW, AttemptOrigin.SYSTEM);
        var nextInstant = Clocks.NOW.plusSeconds(30);

        var next = first.next(nextInstant);

        assertThat(next.id()).isNotEqualTo(first.id());
    }

    @Test
    void shouldReportNotExecutedWhenExecutedAtIsEmpty() {
        var attempt = DeliveryAttempt.first(Ids.EVT_001, 0, Clocks.NOW, AttemptOrigin.SYSTEM);

        assertThat(attempt.isExecuted()).isFalse();
    }

    @Test
    void shouldReportExecutedWhenExecutedAtIsPresent() {
        var attempt = DeliveryAttempt.first(Ids.EVT_001, 0, Clocks.NOW, AttemptOrigin.SYSTEM);

        var executed = attempt.executed(Clocks.NOW.plusSeconds(5), Ids.WORKER_1, DeliveryResult.of(DeliveryOutcomes.success()));

        assertThat(executed.isExecuted()).isTrue();
    }

    @Test
    void shouldRecordResultWhenAttemptIsExecuted() {
        var attempt = DeliveryAttempt.first(Ids.EVT_001, 0, Clocks.NOW, AttemptOrigin.SYSTEM);
        var executedAtInstant = Clocks.NOW.plusSeconds(5);

        var executed = attempt.executed(executedAtInstant, Ids.WORKER_1, DeliveryResult.of(DeliveryOutcomes.success()));

        assertThat(executed.id()).isEqualTo(attempt.id());
        assertThat(executed.executedAt()).contains(executedAtInstant);
        assertThat(executed.claimedBy()).contains(Ids.WORKER_1);
        assertThat(executed.responseStatus()).contains(200);
        assertThat(executed.failureReason()).isEmpty();
        assertThat(executed.latency()).contains(Duration.ofMillis(120));
    }
}
