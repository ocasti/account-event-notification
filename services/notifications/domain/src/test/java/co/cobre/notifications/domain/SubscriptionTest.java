package co.cobre.notifications.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTest {

    private static final ClientId CLIENT_ID = new ClientId("client-1");
    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    @ParameterizedTest(name = "{0}")
    @MethodSource("matchCases")
    void shouldMatchEventKeyWhenSubscriptionCoversIt(
        String caseName, Set<EventKey> subscribedKeys, boolean active, EventKey eventKey, boolean expectedMatch) {

        var subscription = subscription(subscribedKeys, active);

        assertThat(subscription.matches(eventKey)).isEqualTo(expectedMatch);
    }

    private static Stream<Arguments> matchCases() {
        return Stream.of(
            Arguments.of(
                "exact key in set",
                Set.of(new EventKey("user.created"), new EventKey("order.placed")),
                true,
                new EventKey("user.created"),
                true),
            Arguments.of(
                "wildcard in set",
                Set.of(EventKey.wildcard()),
                true,
                new EventKey("any.event.key"),
                true),
            Arguments.of(
                "key not in set",
                Set.of(new EventKey("user.created")),
                true,
                new EventKey("order.placed"),
                false),
            Arguments.of(
                "inactive subscription with matching key",
                Set.of(new EventKey("user.created")),
                false,
                new EventKey("user.created"),
                false),
            Arguments.of(
                "inactive subscription with wildcard",
                Set.of(EventKey.wildcard()),
                false,
                new EventKey("any.event"),
                false));
    }

    private static Subscription subscription(Set<EventKey> subscribedKeys, boolean active) {
        return new Subscription(
            "sub-1",
            CLIENT_ID,
            subscribedKeys,
            new WebhookUrl(URI.create("https://example.com/webhook")),
            Optional.empty(),
            Optional.empty(),
            active,
            NOW
        );
    }
}
