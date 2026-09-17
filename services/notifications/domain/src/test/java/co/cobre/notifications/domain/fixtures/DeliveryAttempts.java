package co.cobre.notifications.domain.fixtures;

import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Object Mother for {@link DeliveryAttempt}. Every variant defaults to
 * {@link Ids#EVT_001}; override with {@link Builder#withEventId(EventId)} to line up with a
 * fixture-built {@link co.cobre.notifications.domain.NotificationEvent}.
 */
public final class DeliveryAttempts {

    private static final Duration PAST_DUE = Duration.ofSeconds(60);
    private static final Duration FUTURE = Duration.ofSeconds(3600);

    private DeliveryAttempts() {
    }

    public static DeliveryAttempt due() {
        return dueAt(Clocks.NOW.minus(PAST_DUE));
    }

    public static DeliveryAttempt dueAt(Instant nextAttemptAt) {
        return anAttempt().withNextAttemptAt(nextAttemptAt).build();
    }

    public static DeliveryAttempt future() {
        return anAttempt().withNextAttemptAt(Clocks.NOW.plus(FUTURE)).build();
    }

    public static DeliveryAttempt executedSuccess() {
        return anAttempt()
            .withExecutedAt(Clocks.NOW)
            .withClaimedBy(Ids.WORKER_1)
            .withResponseStatus(200)
            .withLatency(Duration.ofMillis(120))
            .build();
    }

    public static DeliveryAttempt executedTransientFailure() {
        return anAttempt()
            .withExecutedAt(Clocks.NOW)
            .withClaimedBy(Ids.WORKER_1)
            .withResponseStatus(503)
            .withFailureReason("service unavailable")
            .withLatency(Duration.ofMillis(50))
            .build();
    }

    public static DeliveryAttempt claimedBy(String workerId) {
        return anAttempt().withClaimedAt(Clocks.NOW).withClaimedBy(workerId).build();
    }

    public static DeliveryAttempt replayCycle() {
        return anAttempt().withCycle(1).withAttemptNumber(1).withOrigin(AttemptOrigin.REPLAY).build();
    }

    /**
     * A batch of due attempts for distinct events, deterministic both in event id and in
     * how overdue each attempt is: attempt {@code i} belongs to event {@code evt-batch-i} and
     * became due {@code i} seconds before {@link Clocks#NOW}.
     */
    public static List<DeliveryAttempt> dueBatch(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> anAttempt()
                .withEventId(new EventId("evt-batch-" + i))
                .withNextAttemptAt(Clocks.NOW.minusSeconds(i))
                .build())
            .toList();
    }

    public static Builder anAttempt() {
        return new Builder();
    }

    public static final class Builder {
        private EventId eventId = Ids.EVT_001;
        private int cycle;
        private int attemptNumber = 1;
        private Instant nextAttemptAt = Clocks.NOW;
        private Optional<Instant> claimedAt = Optional.empty();
        private Optional<String> claimedBy = Optional.empty();
        private Optional<Instant> executedAt = Optional.empty();
        private Optional<Integer> responseStatus = Optional.empty();
        private Optional<String> failureReason = Optional.empty();
        private Optional<Duration> latency = Optional.empty();
        private AttemptOrigin origin = AttemptOrigin.SYSTEM;

        private Builder() {
        }

        public Builder withEventId(EventId eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder withCycle(int cycle) {
            this.cycle = cycle;
            return this;
        }

        public Builder withAttemptNumber(int attemptNumber) {
            this.attemptNumber = attemptNumber;
            return this;
        }

        public Builder withNextAttemptAt(Instant nextAttemptAt) {
            this.nextAttemptAt = nextAttemptAt;
            return this;
        }

        public Builder withClaimedAt(Instant claimedAt) {
            this.claimedAt = Optional.of(claimedAt);
            return this;
        }

        public Builder withClaimedBy(String claimedBy) {
            this.claimedBy = Optional.of(claimedBy);
            return this;
        }

        public Builder withExecutedAt(Instant executedAt) {
            this.executedAt = Optional.of(executedAt);
            return this;
        }

        public Builder withResponseStatus(int responseStatus) {
            this.responseStatus = Optional.of(responseStatus);
            return this;
        }

        public Builder withFailureReason(String failureReason) {
            this.failureReason = Optional.of(failureReason);
            return this;
        }

        public Builder withLatency(Duration latency) {
            this.latency = Optional.of(latency);
            return this;
        }

        public Builder withOrigin(AttemptOrigin origin) {
            this.origin = origin;
            return this;
        }

        public DeliveryAttempt build() {
            return new DeliveryAttempt(
                UUID.randomUUID(),
                eventId,
                cycle,
                attemptNumber,
                nextAttemptAt,
                claimedAt,
                claimedBy,
                executedAt,
                responseStatus,
                failureReason,
                latency,
                origin
            );
        }
    }
}
