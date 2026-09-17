package co.cobre.notifications.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEventTest {

    private static final EventId EVENT_ID = new EventId("event-1");
    private static final ClientId CLIENT_ID = new ClientId("client-1");
    private static final EventKey EVENT_KEY = new EventKey("user.created");
    private static final String CONTENT = "test content";
    private static final Instant CREATED_AT = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant RECEIVED_AT = Instant.parse("2024-01-01T00:00:01Z");
    private static final Instant COMPLETED_AT = Instant.parse("2024-01-01T00:01:00Z");

    @Test
    void shouldSetPendingStatusWhenEventIsRegistered() {
        var event = registeredEvent();

        assertThat(event.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(event.cycle()).isZero();
        assertThat(event.subscriptionId()).isPresent();
        assertThat(event.deliveredAt()).isEmpty();
    }

    @Test
    void shouldOmitSubscriptionIdWhenEventIsSkipped() {
        var event = skippedEvent();

        assertThat(event.status()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(event.subscriptionId()).isEmpty();
    }

    @ParameterizedTest(name = "from {0}")
    @MethodSource("pendingOrRetryingEvents")
    void shouldCompleteEventWhenStatusIsPendingOrRetrying(String caseName, Supplier<NotificationEvent> eventSupplier) {
        var event = eventSupplier.get();

        event.complete(COMPLETED_AT);

        assertThat(event.status()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(event.deliveredAt()).contains(COMPLETED_AT);
    }

    @ParameterizedTest(name = "from {0}")
    @MethodSource("pendingOrRetryingEvents")
    void shouldScheduleRetryWhenStatusIsPendingOrRetrying(String caseName, Supplier<NotificationEvent> eventSupplier) {
        var event = eventSupplier.get();

        event.scheduleRetry();

        assertThat(event.status()).isEqualTo(DeliveryStatus.RETRYING);
    }

    @ParameterizedTest(name = "from {0}")
    @MethodSource("pendingOrRetryingEvents")
    void shouldFailEventWhenStatusIsPendingOrRetrying(String caseName, Supplier<NotificationEvent> eventSupplier) {
        var event = eventSupplier.get();

        event.fail();

        assertThat(event.status()).isEqualTo(DeliveryStatus.FAILED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operationsRejectedOnCompletedEvent")
    void shouldRejectStateChangeWhenEventIsCompleted(String caseName, Consumer<NotificationEvent> operation) {
        var event = completedEvent();

        assertThatThrownBy(() -> operation.accept(event))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void shouldResetToPendingWhenReplayingFailedEvent() {
        var event = failedEvent();
        var initialCycle = event.cycle();

        event.replay();

        assertThat(event.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(event.cycle()).isEqualTo(initialCycle + 1);
        assertThat(event.deliveredAt()).isEmpty();
    }

    @ParameterizedTest(name = "from {0}")
    @MethodSource("eventsNotEligibleForReplay")
    void shouldRejectReplayWhenEventIsNotFailed(String caseName, Supplier<NotificationEvent> eventSupplier) {
        var event = eventSupplier.get();

        assertThatThrownBy(event::replay)
            .isInstanceOf(ReplayNotAllowedException.class);
    }

    @Test
    void shouldBeEqualWhenEventIdMatches() {
        var event1 = registeredEvent();
        var event2 = NotificationEvent.register(
            new EventData(EVENT_ID, new ClientId("client-2"), new EventKey("order.created"), "different content", CREATED_AT),
            RECEIVED_AT,
            createSubscription());

        assertThat(event1).isEqualTo(event2);
    }

    @Test
    void shouldShareHashCodeWhenEventIdMatches() {
        var event1 = registeredEvent();
        var event2 = NotificationEvent.register(
            new EventData(EVENT_ID, new ClientId("client-2"), new EventKey("order.created"), "different content", CREATED_AT),
            RECEIVED_AT,
            createSubscription());

        assertThat(event1).hasSameHashCodeAs(event2);
    }

    @Test
    void shouldExposeAllFieldsWhenEventIsRegistered() {
        var event = registeredEvent();

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.clientId()).isEqualTo(CLIENT_ID);
        assertThat(event.eventKey()).isEqualTo(EVENT_KEY);
        assertThat(event.content()).isEqualTo(CONTENT);
        assertThat(event.createdAt()).isEqualTo(CREATED_AT);
        assertThat(event.receivedAt()).isEqualTo(RECEIVED_AT);
    }

    private static Stream<Arguments> pendingOrRetryingEvents() {
        return Stream.of(
            Arguments.of("pending", (Supplier<NotificationEvent>) NotificationEventTest::registeredEvent),
            Arguments.of("retrying", (Supplier<NotificationEvent>) NotificationEventTest::retryingEvent));
    }

    private static Stream<Arguments> eventsNotEligibleForReplay() {
        return Stream.of(
            Arguments.of("pending", (Supplier<NotificationEvent>) NotificationEventTest::registeredEvent),
            Arguments.of("retrying", (Supplier<NotificationEvent>) NotificationEventTest::retryingEvent),
            Arguments.of("completed", (Supplier<NotificationEvent>) NotificationEventTest::completedEvent),
            Arguments.of("skipped", (Supplier<NotificationEvent>) NotificationEventTest::skippedEvent));
    }

    private static Stream<Arguments> operationsRejectedOnCompletedEvent() {
        return Stream.of(
            Arguments.of("complete", (Consumer<NotificationEvent>) event -> event.complete(COMPLETED_AT)),
            Arguments.of("scheduleRetry", (Consumer<NotificationEvent>) NotificationEvent::scheduleRetry),
            Arguments.of("fail", (Consumer<NotificationEvent>) NotificationEvent::fail));
    }

    private static NotificationEvent registeredEvent() {
        var data = new EventData(EVENT_ID, CLIENT_ID, EVENT_KEY, CONTENT, CREATED_AT);
        return NotificationEvent.register(data, RECEIVED_AT, createSubscription());
    }

    private static NotificationEvent skippedEvent() {
        var data = new EventData(EVENT_ID, CLIENT_ID, EVENT_KEY, CONTENT, CREATED_AT);
        return NotificationEvent.skipped(data, RECEIVED_AT);
    }

    private static NotificationEvent retryingEvent() {
        var event = registeredEvent();
        event.scheduleRetry();
        return event;
    }

    private static NotificationEvent completedEvent() {
        var event = registeredEvent();
        event.complete(COMPLETED_AT);
        return event;
    }

    private static NotificationEvent failedEvent() {
        var event = registeredEvent();
        event.fail();
        return event;
    }

    private static Subscription createSubscription() {
        return new Subscription(
            "sub-1",
            CLIENT_ID,
            Set.of(EVENT_KEY),
            new WebhookUrl(URI.create("https://example.com/webhook")),
            Optional.empty(),
            Optional.empty(),
            true,
            CREATED_AT
        );
    }
}
