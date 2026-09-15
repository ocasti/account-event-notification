package co.cobre.notifications.domain;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTest {

    private final ClientId clientId = new ClientId("client-1");
    private final Instant now = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void shouldMatchWhenEventKeyInSet() {
        var subscription = new Subscription(
            "sub-1",
            clientId,
            Set.of(new EventKey("user.created"), new EventKey("order.placed")),
            new WebhookUrl(URI.create("https://example.com/webhook")),
            Optional.empty(),
            Optional.empty(),
            true,
            now
        );
        var eventKey = new EventKey("user.created");

        assertThat(subscription.matches(eventKey)).isTrue();
    }

    @Test
    void shouldMatchAnyKeyWhenWildcardInSet() {
        var subscription = new Subscription(
            "sub-1",
            clientId,
            Set.of(EventKey.wildcard()),
            new WebhookUrl(URI.create("https://example.com/webhook")),
            Optional.empty(),
            Optional.empty(),
            true,
            now
        );
        var eventKey = new EventKey("any.event.key");

        assertThat(subscription.matches(eventKey)).isTrue();
    }

    @Test
    void shouldNotMatchWhenEventKeyNotInSet() {
        var subscription = new Subscription(
            "sub-1",
            clientId,
            Set.of(new EventKey("user.created")),
            new WebhookUrl(URI.create("https://example.com/webhook")),
            Optional.empty(),
            Optional.empty(),
            true,
            now
        );
        var eventKey = new EventKey("order.placed");

        assertThat(subscription.matches(eventKey)).isFalse();
    }

    @Test
    void shouldNotMatchWhenInactive() {
        var subscription = new Subscription(
            "sub-1",
            clientId,
            Set.of(new EventKey("user.created")),
            new WebhookUrl(URI.create("https://example.com/webhook")),
            Optional.empty(),
            Optional.empty(),
            false,
            now
        );
        var eventKey = new EventKey("user.created");

        assertThat(subscription.matches(eventKey)).isFalse();
    }

    @Test
    void shouldNotMatchWhenInactiveEvenWithWildcard() {
        var subscription = new Subscription(
            "sub-1",
            clientId,
            Set.of(EventKey.wildcard()),
            new WebhookUrl(URI.create("https://example.com/webhook")),
            Optional.empty(),
            Optional.empty(),
            false,
            now
        );
        var eventKey = new EventKey("any.event");

        assertThat(subscription.matches(eventKey)).isFalse();
    }
}
