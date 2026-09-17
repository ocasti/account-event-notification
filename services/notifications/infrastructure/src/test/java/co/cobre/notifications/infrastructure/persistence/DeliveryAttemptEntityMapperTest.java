package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.DeliveryAttempts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAttemptEntityMapperTest {

    private final DeliveryAttemptEntityMapper mapper = new DeliveryAttemptEntityMapper();

    @Test
    void shouldPreserveAllDeliveryAttemptFieldsWhenMappingRoundTrip() {
        var domain = DeliveryAttempts.anAttempt()
            .withEventId(new EventId("evt-789"))
            .withCycle(1)
            .withAttemptNumber(2)
            .withNextAttemptAt(Instant.parse("2024-01-15T12:00:00Z"))
            .withClaimedAt(Instant.parse("2024-01-15T11:55:00Z"))
            .withClaimedBy("worker-1")
            .withExecutedAt(Instant.parse("2024-01-15T12:01:00Z"))
            .withResponseStatus(200)
            .withLatency(Duration.ofMillis(150))
            .withOrigin(AttemptOrigin.SYSTEM)
            .build();

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.id()).isEqualTo(domain.id());
        assertThat(mapped.eventId()).isEqualTo(domain.eventId());
        assertThat(mapped.cycle()).isEqualTo(domain.cycle());
        assertThat(mapped.attemptNumber()).isEqualTo(domain.attemptNumber());
        assertThat(mapped.nextAttemptAt()).isEqualTo(domain.nextAttemptAt());
        assertThat(mapped.claimedAt()).isEqualTo(domain.claimedAt());
        assertThat(mapped.claimedBy()).isEqualTo(domain.claimedBy());
        assertThat(mapped.executedAt()).isEqualTo(domain.executedAt());
        assertThat(mapped.responseStatus()).isEqualTo(domain.responseStatus());
        assertThat(mapped.failureReason()).isEqualTo(domain.failureReason());
        assertThat(mapped.latency()).isEqualTo(domain.latency());
        assertThat(mapped.origin()).isEqualTo(domain.origin());
    }

    @Test
    void shouldMapEmptyDeliveryAttemptOptionalsWhenMappingRoundTrip() {
        var domain = DeliveryAttempts.anAttempt()
            .withEventId(new EventId("evt-000"))
            .withNextAttemptAt(Instant.parse("2024-01-15T12:00:00Z"))
            .withOrigin(AttemptOrigin.REPLAY)
            .build();

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.claimedAt()).isEmpty();
        assertThat(mapped.claimedBy()).isEmpty();
        assertThat(mapped.executedAt()).isEmpty();
        assertThat(mapped.responseStatus()).isEmpty();
        assertThat(mapped.failureReason()).isEmpty();
        assertThat(mapped.latency()).isEmpty();
    }

    @ParameterizedTest(name = "origin {0}")
    @EnumSource(AttemptOrigin.class)
    void shouldRoundTripEveryOriginWhenMappingAttempt(AttemptOrigin origin) {
        var domain = DeliveryAttempts.anAttempt()
            .withEventId(new EventId("evt-enum"))
            .withNextAttemptAt(Clocks.NOW)
            .withOrigin(origin)
            .build();

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.origin()).isEqualTo(origin);
    }

    @Test
    void shouldPreserveLatencyInMillisecondsWhenMappingRoundTrip() {
        var domain = DeliveryAttempts.anAttempt()
            .withEventId(new EventId("evt-latency"))
            .withNextAttemptAt(Clocks.NOW)
            .withLatency(Duration.ofMillis(500))
            .build();

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.latency()).isEqualTo(domain.latency());
    }
}
