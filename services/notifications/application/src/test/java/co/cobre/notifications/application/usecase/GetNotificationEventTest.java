package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.usecase.NotificationEventDetail;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventData;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetNotificationEventTest {
    @Mock
    NotificationEventRepository events;
    @Mock
    DeliveryAttemptRepository attempts;

    @Test
    void shouldReturnEventDetailWithAttempts() {
        GetNotificationEvent useCase = new GetNotificationEvent(events, attempts);

        ClientId clientId = new ClientId("client-1");
        EventId eventId = new EventId("evt-123");

        var data = new EventData(
            eventId,
            clientId,
            new EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z")
        );
        var subscription = new co.cobre.notifications.domain.Subscription(
            "sub-123",
            clientId,
            java.util.Set.of(new EventKey("order.created")),
            co.cobre.notifications.domain.WebhookUrl.of("https://example.com/webhook"),
            Optional.empty(),
            Optional.empty(),
            true,
            Instant.parse("2025-01-01T10:00:00Z")
        );
        NotificationEvent event = NotificationEvent.register(
            data,
            Instant.parse("2025-01-01T11:00:01Z"),
            subscription
        );

        DeliveryAttempt attempt1 = DeliveryAttempt.first(
            eventId,
            0,
            Instant.parse("2025-01-01T11:00:01Z"),
            co.cobre.notifications.domain.AttemptOrigin.SYSTEM
        );

        when(events.findByClientAndId(clientId, eventId)).thenReturn(Optional.of(event));
        when(attempts.findByEvent(eventId)).thenReturn(List.of(attempt1));

        NotificationEventDetail result = useCase.get(clientId, eventId);

        assertThat(result.event()).isEqualTo(event);
        assertThat(result.attempts()).containsExactly(attempt1);

        verify(events).findByClientAndId(clientId, eventId);
        verify(attempts).findByEvent(eventId);
    }

    @Test
    void shouldThrowWhenEventNotFound() {
        GetNotificationEvent useCase = new GetNotificationEvent(events, attempts);

        ClientId clientId = new ClientId("client-1");
        EventId eventId = new EventId("evt-nonexistent");

        when(events.findByClientAndId(clientId, eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(clientId, eventId))
            .isInstanceOf(NotificationEventNotFoundException.class);

        verify(attempts, never()).findByEvent(eventId);
    }
}
