package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.infrastructure.persistence.DeliveryStatusEntity;
import co.cobre.notifications.infrastructure.persistence.NotificationEventEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationEventEntityMapperTest {

    private final NotificationEventEntityMapper mapper = new NotificationEventEntityMapper();

    @Test
    void testRoundTripPreservesAllFields() {
        var eventId = new EventId("evt-123");
        var clientId = new ClientId("client-001");
        var eventKey = new EventKey("order.created");
        var content = "{\"order_id\": \"123\"}";
        var createdAt = Instant.parse("2024-01-10T10:00:00Z");
        var receivedAt = Instant.parse("2024-01-10T10:00:01Z");
        var subscriptionId = Optional.of("sub-456");
        var deliveredAt = Optional.of(Instant.parse("2024-01-10T10:05:00Z"));
        var cycle = 2;

        var domain = new NotificationEvent(
            eventId,
            clientId,
            eventKey,
            content,
            createdAt,
            receivedAt,
            DeliveryStatus.COMPLETED,
            subscriptionId,
            cycle,
            deliveredAt
        );

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertEquals(domain.eventId(), mapped.eventId());
        assertEquals(domain.clientId(), mapped.clientId());
        assertEquals(domain.eventKey(), mapped.eventKey());
        assertEquals(domain.content(), mapped.content());
        assertEquals(domain.createdAt(), mapped.createdAt());
        assertEquals(domain.receivedAt(), mapped.receivedAt());
        assertEquals(domain.status(), mapped.status());
        assertEquals(domain.subscriptionId(), mapped.subscriptionId());
        assertEquals(domain.cycle(), mapped.cycle());
        assertEquals(domain.deliveredAt(), mapped.deliveredAt());
    }

    @Test
    void testRoundTripPreservesEmptyOptionals() {
        var eventId = new EventId("evt-456");
        var clientId = new ClientId("client-002");
        var eventKey = new EventKey("payment.failed");
        var content = "{\"error\": \"timeout\"}";
        var createdAt = Instant.parse("2024-01-11T14:30:00Z");
        var receivedAt = Instant.parse("2024-01-11T14:30:02Z");

        var domain = new NotificationEvent(
            eventId,
            clientId,
            eventKey,
            content,
            createdAt,
            receivedAt,
            DeliveryStatus.FAILED,
            Optional.empty(),
            0,
            Optional.empty()
        );

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertEquals(domain.eventId(), mapped.eventId());
        assertEquals(domain.clientId(), mapped.clientId());
        assertFalse(mapped.subscriptionId().isPresent());
        assertFalse(mapped.deliveredAt().isPresent());
    }

    @Test
    void testEnumMappingForAllStatuses() {
        for (DeliveryStatus status : DeliveryStatus.values()) {
            var domain = new NotificationEvent(
                new EventId("evt-" + status),
                new ClientId("client-001"),
                new EventKey("test"),
                "{}",
                Instant.now(),
                Instant.now(),
                status,
                Optional.empty(),
                0,
                Optional.empty()
            );

            var entity = mapper.toEntity(domain);
            var mapped = mapper.toDomain(entity);

            assertEquals(status, mapped.status());
        }
    }
}
