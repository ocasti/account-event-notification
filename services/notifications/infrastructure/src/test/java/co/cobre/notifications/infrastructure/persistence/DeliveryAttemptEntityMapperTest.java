package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAttemptEntityMapperTest {

    private final DeliveryAttemptEntityMapper mapper = new DeliveryAttemptEntityMapper();

    @Test
    void shouldPreserveAllDeliveryAttemptFieldsWhenMappingRoundTrip() {
        var id = UUID.randomUUID();
        var eventId = new EventId("evt-789");
        var cycle = 1;
        var attemptNumber = 2;
        var nextAttemptAt = Instant.parse("2024-01-15T12:00:00Z");
        var claimedAt = Optional.of(Instant.parse("2024-01-15T11:55:00Z"));
        var claimedBy = Optional.of("worker-1");
        var executedAt = Optional.of(Instant.parse("2024-01-15T12:01:00Z"));
        var responseStatus = Optional.of(200);
        var failureReason = Optional.<String>empty();
        var latency = Optional.of(Duration.ofMillis(150));
        var domain = new DeliveryAttempt(
            id,
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
            AttemptOrigin.SYSTEM
        );

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
        var domain = new DeliveryAttempt(
            UUID.randomUUID(),
            new EventId("evt-000"),
            0,
            1,
            Instant.parse("2024-01-15T12:00:00Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            AttemptOrigin.REPLAY
        );

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
        var domain = new DeliveryAttempt(
            UUID.randomUUID(),
            new EventId("evt-enum"),
            0,
            1,
            Instant.now(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            origin
        );

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.origin()).isEqualTo(origin);
    }

    @Test
    void shouldPreserveLatencyInMillisecondsWhenMappingRoundTrip() {
        var domain = new DeliveryAttempt(
            UUID.randomUUID(),
            new EventId("evt-latency"),
            0,
            1,
            Instant.now(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(Duration.ofMillis(500)),
            AttemptOrigin.SYSTEM
        );

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.latency()).isEqualTo(domain.latency());
    }
}
