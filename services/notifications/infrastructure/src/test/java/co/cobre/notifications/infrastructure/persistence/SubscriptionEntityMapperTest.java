package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.domain.WebhookUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionEntityMapperTest {

    private final SubscriptionEntityMapper mapper = new SubscriptionEntityMapper();

    @Test
    void shouldPreserveAllSubscriptionFieldsWhenMappingRoundTrip() {
        var id = "sub-001";
        var clientId = new ClientId("client-001");
        var eventKeys = Set.of(
            new EventKey("order.created"),
            new EventKey("payment.completed")
        );
        var url = WebhookUrl.of("https://example.com/webhook");
        var description = Optional.of("Production webhook");
        var signatureKey = Optional.of("sk-prod-12345");
        var createdAt = Instant.parse("2024-01-10T08:00:00Z");
        var domain = new Subscription(
            id,
            clientId,
            eventKeys,
            url,
            description,
            signatureKey,
            true,
            createdAt
        );

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
        var domain = new Subscription(
            "sub-002",
            new ClientId("client-002"),
            Set.of(new EventKey("event.test")),
            WebhookUrl.of("https://test.com/webhook"),
            Optional.empty(),
            Optional.empty(),
            false,
            Instant.parse("2024-01-11T10:00:00Z")
        );

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.description()).isEmpty();
        assertThat(mapped.signatureKey()).isEmpty();
    }

    @ParameterizedTest(name = "active={0}")
    @ValueSource(booleans = {true, false})
    void shouldPreserveActiveFlagWhenMappingRoundTrip(boolean active) {
        var domain = new Subscription(
            "sub-active-flag",
            new ClientId("client-active-flag"),
            Set.of(new EventKey("test")),
            WebhookUrl.of("https://example.com"),
            Optional.empty(),
            Optional.empty(),
            active,
            Instant.now()
        );

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
        var domain = new Subscription(
            "sub-multi",
            new ClientId("client-multi"),
            eventKeys,
            WebhookUrl.of("https://example.com"),
            Optional.empty(),
            Optional.empty(),
            true,
            Instant.now()
        );

        var entity = mapper.toEntity(domain);
        var mapped = mapper.toDomain(entity);

        assertThat(mapped.eventKeys()).isEqualTo(eventKeys);
    }
}
