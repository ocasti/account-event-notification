package co.cobre.notifications.domain;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.Ids;
import co.cobre.notifications.domain.fixtures.NotificationEvents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEventTest {

    private static final Instant COMPLETED_AT = Clocks.NOW.plusSeconds(60);

    @Test
    void shouldSetPendingStatusWhenEventIsRegistered() {
        var event = NotificationEvents.pending();

        assertThat(event.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(event.cycle()).isZero();
        assertThat(event.subscriptionId()).isPresent();
        assertThat(event.deliveredAt()).isEmpty();
    }

    @Test
    void shouldOmitSubscriptionIdWhenEventIsSkipped() {
        var event = NotificationEvents.skipped();

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
        var event = NotificationEvents.completed();

        assertThatThrownBy(() -> operation.accept(event))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void shouldResetToPendingWhenReplayingFailedEvent() {
        var event = NotificationEvents.failed();
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
        var pendingEvent = NotificationEvents.pending();
        var eventWithSameId = NotificationEvents.aPendingEvent()
            .withEventId(Ids.EVT_001)
            .withClientId(Ids.CLIENT_002)
            .withEventKey(Ids.CREDIT_TRANSFER)
            .build();

        assertThat(pendingEvent).isEqualTo(eventWithSameId);
    }

    @Test
    void shouldShareHashCodeWhenEventIdMatches() {
        var pendingEvent = NotificationEvents.pending();
        var eventWithSameId = NotificationEvents.aPendingEvent()
            .withEventId(Ids.EVT_001)
            .withClientId(Ids.CLIENT_002)
            .withEventKey(Ids.CREDIT_TRANSFER)
            .build();

        assertThat(pendingEvent).hasSameHashCodeAs(eventWithSameId);
    }

    @Test
    void shouldExposeAllFieldsWhenEventIsRegistered() {
        var createdAt = Clocks.NOW.minusSeconds(30);

        var event = NotificationEvents.aPendingEvent().withCreatedAt(createdAt).build();

        assertThat(event.eventId()).isEqualTo(Ids.EVT_001);
        assertThat(event.clientId()).isEqualTo(Ids.CLIENT_001);
        assertThat(event.eventKey()).isEqualTo(Ids.CREDIT_CARD_PAYMENT);
        assertThat(event.content()).isEqualTo("Credit card payment received for $150.00");
        assertThat(event.createdAt()).isEqualTo(createdAt);
        assertThat(event.receivedAt()).isEqualTo(Clocks.NOW);
    }

    private static Stream<Arguments> pendingOrRetryingEvents() {
        return Stream.of(
            Arguments.of("pending", (Supplier<NotificationEvent>) NotificationEvents::pending),
            Arguments.of("retrying", (Supplier<NotificationEvent>) NotificationEvents::retrying));
    }

    private static Stream<Arguments> eventsNotEligibleForReplay() {
        return Stream.of(
            Arguments.of("pending", (Supplier<NotificationEvent>) NotificationEvents::pending),
            Arguments.of("retrying", (Supplier<NotificationEvent>) NotificationEvents::retrying),
            Arguments.of("completed", (Supplier<NotificationEvent>) NotificationEvents::completed),
            Arguments.of("skipped", (Supplier<NotificationEvent>) NotificationEvents::skipped));
    }

    private static Stream<Arguments> operationsRejectedOnCompletedEvent() {
        return Stream.of(
            Arguments.of("complete", (Consumer<NotificationEvent>) event -> event.complete(COMPLETED_AT)),
            Arguments.of("scheduleRetry", (Consumer<NotificationEvent>) NotificationEvent::scheduleRetry),
            Arguments.of("fail", (Consumer<NotificationEvent>) NotificationEvent::fail));
    }
}
