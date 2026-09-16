package co.cobre.notifications.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEventTest {

    private final EventId eventId = new EventId("event-1");
    private final ClientId clientId = new ClientId("client-1");
    private final EventKey eventKey = new EventKey("user.created");
    private final String content = "test content";
    private final Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
    private final Instant receivedAt = Instant.parse("2024-01-01T00:00:01Z");

    @Test
    void shouldRegisterEventWithPendingStatusAndZeroCycle() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);

        assertThat(event.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(event.cycle()).isZero();
        assertThat(event.subscriptionId()).isPresent();
        assertThat(event.deliveredAt()).isEmpty();
    }

    @Test
    void shouldCreateSkippedEventWithoutSubscriptionId() {
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.skipped(data, receivedAt);

        assertThat(event.status()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(event.subscriptionId()).isEmpty();
    }

    @Test
    void shouldCompleteEventFromPendingStatus() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);
        var completedAt = Instant.parse("2024-01-01T00:01:00Z");

        event.complete(completedAt);

        assertThat(event.status()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(event.deliveredAt()).isPresent().contains(completedAt);
    }

    @Test
    void shouldCompleteEventFromRetryingStatus() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);
        event.scheduleRetry();
        var completedAt = Instant.parse("2024-01-01T00:01:00Z");

        event.complete(completedAt);

        assertThat(event.status()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(event.deliveredAt()).isPresent().contains(completedAt);
    }

    @Test
    void shouldThrowWhenCompletingAlreadyCompletedEvent() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);
        var completedAt = Instant.parse("2024-01-01T00:01:00Z");
        event.complete(completedAt);

        assertThatThrownBy(() -> event.complete(completedAt))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void shouldScheduleRetryFromPendingStatus() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);

        event.scheduleRetry();

        assertThat(event.status()).isEqualTo(DeliveryStatus.RETRYING);
    }

    @Test
    void shouldScheduleRetryFromRetryingStatus() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);
        event.scheduleRetry();

        event.scheduleRetry();

        assertThat(event.status()).isEqualTo(DeliveryStatus.RETRYING);
    }

    @Test
    void shouldFailEventFromPendingStatus() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);

        event.fail();

        assertThat(event.status()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void shouldFailEventFromRetryingStatus() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);
        event.scheduleRetry();

        event.fail();

        assertThat(event.status()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void shouldReplayEventFromFailedStatus() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);
        event.fail();
        var initialCycle = event.cycle();

        event.replay();

        assertThat(event.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(event.cycle()).isEqualTo(initialCycle + 1);
        assertThat(event.deliveredAt()).isEmpty();
    }

    @Test
    void shouldThrowWhenReplayingFromCompletedStatus() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);
        var completedAt = Instant.parse("2024-01-01T00:01:00Z");
        event.complete(completedAt);

        assertThatThrownBy(event::replay)
            .isInstanceOf(ReplayNotAllowedException.class);
    }

    @Test
    void shouldThrowWhenReplayingFromPendingStatus() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);

        assertThatThrownBy(event::replay)
            .isInstanceOf(ReplayNotAllowedException.class);
    }

    @Test
    void shouldThrowWhenReplayingFromRetryingStatus() {
        var subscription = createSubscription();
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.register(data, receivedAt, subscription);
        event.scheduleRetry();

        assertThatThrownBy(event::replay)
            .isInstanceOf(ReplayNotAllowedException.class);
    }

    @Test
    void shouldThrowWhenReplayingFromSkippedStatus() {
        var data = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event = NotificationEvent.skipped(data, receivedAt);

        assertThatThrownBy(event::replay)
            .isInstanceOf(ReplayNotAllowedException.class);
    }

    @Test
    void shouldBeEqualByEventId() {
        var subscription = createSubscription();
        var data1 = new EventData(eventId, clientId, eventKey, content, createdAt);
        var event1 = NotificationEvent.register(data1, receivedAt, subscription);
        var data2 = new EventData(eventId, new ClientId("client-2"), new EventKey("order.created"),
            "different content", createdAt);
        var event2 = NotificationEvent.register(data2, receivedAt, subscription);

        assertThat(event1).isEqualTo(event2);
    }

    private Subscription createSubscription() {
        return new Subscription(
            "sub-1",
            clientId,
            Set.of(eventKey),
            new WebhookUrl(java.net.URI.create("https://example.com/webhook")),
            Optional.empty(),
            Optional.empty(),
            true,
            createdAt
        );
    }
}
