package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.domain.WebhookUrl;
import co.cobre.notifications.infrastructure.persistence.SubscriptionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionEntityMapperTest {

    private final SubscriptionEntityMapper mapper = new SubscriptionEntityMapper();

    @Test
    void testRoundTripPreservesAllFields() {
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

        assertEquals(domain.id(), mapped.id());
        assertEquals(domain.clientId(), mapped.clientId());
        assertEquals(domain.eventKeys(), mapped.eventKeys());
        assertEquals(domain.url(), mapped.url());
        assertEquals(domain.description(), mapped.description());
        assertEquals(domain.signatureKey(), mapped.signatureKey());
        assertEquals(domain.active(), mapped.active());
        assertEquals(domain.createdAt(), mapped.createdAt());
    }

    @Test
    void testRoundTripPreservesEmptyOptionals() {
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

        assertFalse(mapped.description().isPresent());
        assertFalse(mapped.signatureKey().isPresent());
    }

    @Test
    void testActiveFieldMapping() {
        var activeDomain = new Subscription(
            "sub-active",
            new ClientId("client-active"),
            Set.of(new EventKey("test")),
            WebhookUrl.of("https://example.com"),
            Optional.empty(),
            Optional.empty(),
            true,
            Instant.now()
        );

        var inactiveDomain = new Subscription(
            "sub-inactive",
            new ClientId("client-inactive"),
            Set.of(new EventKey("test")),
            WebhookUrl.of("https://example.com"),
            Optional.empty(),
            Optional.empty(),
            false,
            Instant.now()
        );

        assertTrue(mapper.toDomain(mapper.toEntity(activeDomain)).active());
        assertFalse(mapper.toDomain(mapper.toEntity(inactiveDomain)).active());
    }

    @Test
    void testMultipleEventKeysMapping() {
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

        assertEquals(eventKeys, mapped.eventKeys());
    }
}
