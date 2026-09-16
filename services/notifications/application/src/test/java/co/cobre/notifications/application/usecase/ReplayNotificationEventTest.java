package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.ReplayNotAllowedException;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
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

    @Test
    void shouldReplayFailedEvent() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, clock);

        ClientId clientId = new ClientId("client-1");
        EventId eventId = new EventId("evt-123");

        NotificationEvent failedEvent = new NotificationEvent(
            eventId,
            clientId,
            new EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z"),
            DeliveryStatus.FAILED,
            Optional.of("sub-123"),
            2,
            Optional.empty()
        );

        when(events.findByClientAndId(clientId, eventId)).thenReturn(Optional.of(failedEvent));
        when(events.transition(eq(eventId), eq(DeliveryStatus.FAILED), any()))
            .thenReturn(true);

        ReplayResult result = useCase.replay(clientId, eventId);

        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.cycle()).isEqualTo(3);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        ArgumentCaptor<DeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);

        verify(events).transition(org.mockito.ArgumentMatchers.eq(eventId), org.mockito.ArgumentMatchers.eq(DeliveryStatus.FAILED), eventCaptor.capture());
        verify(attempts).save(attemptCaptor.capture());

        NotificationEvent updatedEvent = eventCaptor.getValue();
        assertThat(updatedEvent.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(updatedEvent.cycle()).isEqualTo(3);

        DeliveryAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.eventId()).isEqualTo(eventId);
        assertThat(attempt.cycle()).isEqualTo(3);
        assertThat(attempt.attemptNumber()).isEqualTo(1);
        assertThat(attempt.origin()).isEqualTo(AttemptOrigin.REPLAY);
        assertThat(attempt.nextAttemptAt()).isEqualTo(now);
    }

    @Test
    void shouldThrowWhenTransitionFails() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, clock);

        ClientId clientId = new ClientId("client-1");
        EventId eventId = new EventId("evt-456");

        NotificationEvent failedEvent = new NotificationEvent(
            eventId,
            clientId,
            new EventKey("user.deleted"),
            "User deleted",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z"),
            DeliveryStatus.FAILED,
            Optional.of("sub-456"),
            1,
            Optional.empty()
        );

        when(events.findByClientAndId(clientId, eventId)).thenReturn(Optional.of(failedEvent));
        when(events.transition(eq(eventId), eq(DeliveryStatus.FAILED), any()))
            .thenReturn(false);

        assertThatThrownBy(() -> useCase.replay(clientId, eventId))
            .isInstanceOf(ReplayNotAllowedException.class);

        verify(attempts, never()).save(any());
    }

    @Test
    void shouldThrowWhenEventNotInFailedStatus() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, clock);

        ClientId clientId = new ClientId("client-1");
        EventId eventId = new EventId("evt-789");

        NotificationEvent pendingEvent = new NotificationEvent(
            eventId,
            clientId,
            new EventKey("payment.completed"),
            "Payment completed",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z"),
            DeliveryStatus.PENDING,
            Optional.of("sub-789"),
            0,
            Optional.empty()
        );

        when(events.findByClientAndId(clientId, eventId)).thenReturn(Optional.of(pendingEvent));

        assertThatThrownBy(() -> useCase.replay(clientId, eventId))
            .isInstanceOf(ReplayNotAllowedException.class);

        verify(events, never()).transition(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(attempts, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrowWhenEventNotFound() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, clock);

        ClientId clientId = new ClientId("client-1");
        EventId eventId = new EventId("evt-nonexistent");

        when(events.findByClientAndId(clientId, eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.replay(clientId, eventId))
            .isInstanceOf(NotificationEventNotFoundException.class);

        verify(events, never()).transition(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(attempts, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
