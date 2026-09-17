package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventEntityMapperTest {

    private final NotificationEventEntityMapper mapper = new NotificationEventEntityMapper();

    @Test
    void shouldPreserveAllNotificationEventFieldsWhenMappingRoundTrip() {
        var domain = NotificationEvents.aPendingEvent()
            .withEventId(new EventId("evt-123"))
            .withClientId(new ClientId("client-001"))
            .withEventKey(new EventKey("order.created"))
            .withStatus(DeliveryStatus.COMPLETED)
            .withCycle(2)
            .withSubscriptionId("sub-456")
            .build();

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
        var domain = NotificationEvents.skipped();

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
        var domain = NotificationEvents.aPendingEvent()
            .withEventId(new EventId("evt-" + status))
            .withStatus(status)
            .build();

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.status()).isEqualTo(status);
    }
}
