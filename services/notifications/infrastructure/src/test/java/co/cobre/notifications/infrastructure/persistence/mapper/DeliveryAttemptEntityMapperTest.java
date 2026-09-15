package co.cobre.notifications.infrastructure.persistence.mapper;

import co.cobre.notifications.domain.model.AttemptOrigin;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.infrastructure.persistence.entity.DeliveryAttemptEntity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryAttemptEntityMapperTest {

    private final DeliveryAttemptEntityMapper mapper = new DeliveryAttemptEntityMapper();

    @Test
    void testRoundTripPreservesAllFields() {
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

        assertEquals(domain.id(), mapped.id());
        assertEquals(domain.eventId(), mapped.eventId());
        assertEquals(domain.cycle(), mapped.cycle());
        assertEquals(domain.attemptNumber(), mapped.attemptNumber());
        assertEquals(domain.nextAttemptAt(), mapped.nextAttemptAt());
        assertEquals(domain.claimedAt(), mapped.claimedAt());
        assertEquals(domain.claimedBy(), mapped.claimedBy());
        assertEquals(domain.executedAt(), mapped.executedAt());
        assertEquals(domain.responseStatus(), mapped.responseStatus());
        assertEquals(domain.failureReason(), mapped.failureReason());
        assertEquals(domain.latency(), mapped.latency());
        assertEquals(domain.origin(), mapped.origin());
    }

    @Test
    void testRoundTripPreservesEmptyOptionals() {
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

        assertFalse(mapped.claimedAt().isPresent());
        assertFalse(mapped.claimedBy().isPresent());
        assertFalse(mapped.executedAt().isPresent());
        assertFalse(mapped.responseStatus().isPresent());
        assertFalse(mapped.failureReason().isPresent());
        assertFalse(mapped.latency().isPresent());
    }

    @Test
    void testEnumMappingForOrigins() {
        for (AttemptOrigin origin : AttemptOrigin.values()) {
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

            assertEquals(origin, mapped.origin());
        }
    }

    @Test
    void testLatencyAsMilliseconds() {
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

        assertEquals(domain.latency(), mapped.latency());
    }
}
