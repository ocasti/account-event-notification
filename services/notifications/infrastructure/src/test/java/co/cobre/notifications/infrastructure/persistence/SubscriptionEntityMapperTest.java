package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.WebhookUrl;
import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.Subscriptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionEntityMapperTest {

    private final SubscriptionEntityMapper mapper = new SubscriptionEntityMapper();

    @Test
    void shouldPreserveAllSubscriptionFieldsWhenMappingRoundTrip() {
        var domain = Subscriptions.aSubscription()
            .withId("sub-001")
            .withClientId(new ClientId("client-001"))
            .withEventKeys(Set.of(new EventKey("order.created"), new EventKey("payment.completed")))
            .withUrl(WebhookUrl.of("https://example.com/webhook"))
            .withDescription("Production webhook")
            .withSignatureKey("sk-prod-12345")
            .withCreatedAt(Instant.parse("2024-01-10T08:00:00Z"))
            .build();

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.id()).isEqualTo(domain.id());
        assertThat(mapped.clientId()).isEqualTo(domain.clientId());
        assertThat(mapped.eventKeys()).isEqualTo(domain.eventKeys());
        assertThat(mapped.url()).isEqualTo(domain.url());
        assertThat(mapped.description()).isEqualTo(domain.description());
        assertThat(mapped.signatureKey()).isEqualTo(domain.signatureKey());
        assertThat(mapped.active()).isEqualTo(domain.active());
        assertThat(mapped.createdAt()).isEqualTo(domain.createdAt());
    }

    @Test
    void shouldMapEmptySubscriptionOptionalsWhenMappingRoundTrip() {
        var domain = Subscriptions.aSubscription()
            .withId("sub-002")
            .withClientId(new ClientId("client-002"))
            .withEventKeys(Set.of(new EventKey("event.test")))
            .withUrl(WebhookUrl.of("https://test.com/webhook"))
            .withActive(false)
            .withCreatedAt(Instant.parse("2024-01-11T10:00:00Z"))
            .build();

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.description()).isEmpty();
        assertThat(mapped.signatureKey()).isEmpty();
    }

    @ParameterizedTest(name = "active={0}")
    @ValueSource(booleans = {true, false})
    void shouldPreserveActiveFlagWhenMappingRoundTrip(boolean active) {
        var domain = Subscriptions.aSubscription()
            .withId("sub-active-flag")
            .withClientId(new ClientId("client-active-flag"))
            .withEventKeys(Set.of(new EventKey("test")))
            .withUrl(WebhookUrl.of("https://example.com"))
            .withActive(active)
            .withCreatedAt(Clocks.NOW)
            .build();

        var mapped = mapper.toDomain(mapper.toEntity(domain));

        assertThat(mapped.active()).isEqualTo(active);
    }

    @Test
    void shouldPreserveAllEventKeysWhenMappingRoundTrip() {
        var eventKeys = Set.of(
            new EventKey("order.created"),
            new EventKey("order.updated"),
            new EventKey("order.cancelled"),
            new EventKey("*")
        );
        var domain = Subscriptions.aSubscription()
            .withId("sub-multi")
            .withClientId(new ClientId("client-multi"))
            .withEventKeys(eventKeys)
            .withUrl(WebhookUrl.of("https://example.com"))
            .withCreatedAt(Clocks.NOW)
            .build();

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.eventKeys()).isEqualTo(eventKeys);
    }
}
