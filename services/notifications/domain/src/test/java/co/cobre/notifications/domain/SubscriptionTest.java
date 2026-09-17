package co.cobre.notifications.domain;

import java.util.Set;
import java.util.stream.Stream;

import co.cobre.notifications.domain.fixtures.Subscriptions;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("matchCases")
    void shouldMatchEventKeyWhenSubscriptionCoversIt(
        String caseName, Set<EventKey> subscribedKeys, boolean active, EventKey eventKey, boolean expectedMatch) {

        var subscription = Subscriptions.aSubscription()
            .withEventKeys(subscribedKeys)
            .withActive(active)
            .build();

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
}
