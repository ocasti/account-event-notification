package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.ReplayNotAllowedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayNotificationEventTest {
    @Mock
    NotificationEventRepository events;
    @Mock
    DeliveryAttemptRepository attempts;

    private static final Instant NOW = Instant.parse("2025-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    private NotificationEvent eventWithStatus(EventId eventId, ClientId clientId, EventKey eventKey, String content,
                                               String subscriptionId, DeliveryStatus status, int cycle) {
        return new NotificationEvent(
            eventId, clientId, eventKey, content,
            Instant.parse("2025-01-01T11:00:00Z"), Instant.parse("2025-01-01T11:00:01Z"),
            status, Optional.of(subscriptionId), cycle, Optional.empty()
        );
    }

    @Test
    void shouldReplayEventWhenStatusIsFailed() {
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, CLOCK);
        ClientId clientId = new ClientId("client-1");
        EventId eventId = new EventId("evt-123");
        NotificationEvent failedEvent = eventWithStatus(
            eventId, clientId, new EventKey("order.created"), "Order created", "sub-123", DeliveryStatus.FAILED, 2
        );
        when(events.findByClientAndId(clientId, eventId)).thenReturn(Optional.of(failedEvent));
        when(events.transition(eq(eventId), eq(DeliveryStatus.FAILED), any())).thenReturn(true);

        ReplayResult result = useCase.replay(clientId, eventId);

        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.cycle()).isEqualTo(3);
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        ArgumentCaptor<DeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(events).transition(eq(eventId), eq(DeliveryStatus.FAILED), eventCaptor.capture());
        verify(attempts).save(attemptCaptor.capture());
        NotificationEvent updatedEvent = eventCaptor.getValue();
        assertThat(updatedEvent.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(updatedEvent.cycle()).isEqualTo(3);
        DeliveryAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.eventId()).isEqualTo(eventId);
        assertThat(attempt.cycle()).isEqualTo(3);
        assertThat(attempt.attemptNumber()).isEqualTo(1);
        assertThat(attempt.origin()).isEqualTo(AttemptOrigin.REPLAY);
        assertThat(attempt.nextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void shouldThrowWhenTransitionToPendingFails() {
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, CLOCK);
        ClientId clientId = new ClientId("client-1");
        EventId eventId = new EventId("evt-456");
        NotificationEvent failedEvent = eventWithStatus(
            eventId, clientId, new EventKey("user.deleted"), "User deleted", "sub-456", DeliveryStatus.FAILED, 1
        );
        when(events.findByClientAndId(clientId, eventId)).thenReturn(Optional.of(failedEvent));
        when(events.transition(eq(eventId), eq(DeliveryStatus.FAILED), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.replay(clientId, eventId))
            .isInstanceOf(ReplayNotAllowedException.class);

        verify(attempts, never()).save(any());
    }

    @Test
    void shouldThrowWhenEventStatusIsNotFailed() {
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, CLOCK);
        ClientId clientId = new ClientId("client-1");
        EventId eventId = new EventId("evt-789");
        NotificationEvent pendingEvent = eventWithStatus(
            eventId, clientId, new EventKey("payment.completed"), "Payment completed", "sub-789", DeliveryStatus.PENDING, 0
        );
        when(events.findByClientAndId(clientId, eventId)).thenReturn(Optional.of(pendingEvent));

        assertThatThrownBy(() -> useCase.replay(clientId, eventId))
            .isInstanceOf(ReplayNotAllowedException.class);

        verify(events, never()).transition(any(), any(), any());
        verify(attempts, never()).save(any());
    }

    @Test
    void shouldThrowWhenEventNotFoundForReplay() {
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, CLOCK);
        ClientId clientId = new ClientId("client-1");
        EventId eventId = new EventId("evt-nonexistent");
        when(events.findByClientAndId(clientId, eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.replay(clientId, eventId))
            .isInstanceOf(NotificationEventNotFoundException.class);

        verify(events, never()).transition(any(), any(), any());
        verify(attempts, never()).save(any());
    }
}
