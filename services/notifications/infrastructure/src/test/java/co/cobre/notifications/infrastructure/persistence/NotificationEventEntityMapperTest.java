package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventEntityMapperTest {

    private final NotificationEventEntityMapper mapper = new NotificationEventEntityMapper();

    @Test
    void shouldPreserveAllNotificationEventFieldsWhenMappingRoundTrip() {
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

        assertThat(mapped.eventId()).isEqualTo(domain.eventId());
        assertThat(mapped.clientId()).isEqualTo(domain.clientId());
        assertThat(mapped.eventKey()).isEqualTo(domain.eventKey());
        assertThat(mapped.content()).isEqualTo(domain.content());
        assertThat(mapped.createdAt()).isEqualTo(domain.createdAt());
        assertThat(mapped.receivedAt()).isEqualTo(domain.receivedAt());
        assertThat(mapped.status()).isEqualTo(domain.status());
        assertThat(mapped.subscriptionId()).isEqualTo(domain.subscriptionId());
        assertThat(mapped.cycle()).isEqualTo(domain.cycle());
        assertThat(mapped.deliveredAt()).isEqualTo(domain.deliveredAt());
    }

    @Test
    void shouldMapEmptyNotificationEventOptionalsWhenMappingRoundTrip() {
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

        assertThat(mapped.eventId()).isEqualTo(domain.eventId());
        assertThat(mapped.clientId()).isEqualTo(domain.clientId());
        assertThat(mapped.subscriptionId()).isEmpty();
        assertThat(mapped.deliveredAt()).isEmpty();
    }

    @ParameterizedTest(name = "status {0}")
    @EnumSource(DeliveryStatus.class)
    void shouldRoundTripEveryStatusWhenMappingEvent(DeliveryStatus status) {
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

        assertThat(mapped.status()).isEqualTo(status);
    }
}
