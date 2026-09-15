package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.command.RegisterEventCommand;
import co.cobre.notifications.application.command.RegistrationResult;
import co.cobre.notifications.application.port.out.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.port.out.SubscriptionRepository;
import co.cobre.notifications.domain.model.AttemptOrigin;
import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.domain.model.EventKey;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.domain.model.Subscription;
import co.cobre.notifications.domain.model.WebhookUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterNotificationEventTest {
    @Mock
    NotificationEventRepository events;
    @Mock
    DeliveryAttemptRepository attempts;
    @Mock
    SubscriptionRepository subscriptions;

    @Test
    void shouldRegisterEventWithActiveSubscription() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RegisterNotificationEvent useCase = new RegisterNotificationEvent(events, attempts, subscriptions, clock);

        EventId eventId = new EventId("evt-123");
        ClientId clientId = new ClientId("client-1");
        EventKey eventKey = new EventKey("order.created");
        String content = "Order created";
        Instant occurredAt = Instant.parse("2025-01-01T11:50:00Z");

        Subscription subscription = new Subscription(
            "sub-123",
            clientId,
            Set.of(eventKey),
            WebhookUrl.of("https://example.com/webhook"),
            Optional.empty(),
            Optional.empty(),
            true,
            Instant.parse("2025-01-01T10:00:00Z")
        );

        RegisterEventCommand command = new RegisterEventCommand(eventId, clientId, eventKey, content, occurredAt);

        when(subscriptions.findActive(clientId, eventKey)).thenReturn(Optional.of(subscription));
        when(events.existsById(eventId)).thenReturn(false);

        RegistrationResult result = useCase.register(command);

        assertThat(result).isEqualTo(RegistrationResult.REGISTERED);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(eventCaptor.capture());

        NotificationEvent saved = eventCaptor.getValue();
        assertThat(saved.eventId()).isEqualTo(eventId);
        assertThat(saved.clientId()).isEqualTo(clientId);
        assertThat(saved.eventKey()).isEqualTo(eventKey);
        assertThat(saved.content()).isEqualTo(content);
        assertThat(saved.createdAt()).isEqualTo(occurredAt);
        assertThat(saved.receivedAt()).isEqualTo(now);
        assertThat(saved.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(saved.subscriptionId()).contains("sub-123");
        assertThat(saved.cycle()).isZero();

        ArgumentCaptor<DeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attempts).save(attemptCaptor.capture());

        DeliveryAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.eventId()).isEqualTo(eventId);
        assertThat(attempt.cycle()).isZero();
        assertThat(attempt.attemptNumber()).isEqualTo(1);
        assertThat(attempt.nextAttemptAt()).isEqualTo(now);
        assertThat(attempt.origin()).isEqualTo(AttemptOrigin.SYSTEM);
    }

    @Test
    void shouldSkipEventWithoutActiveSubscription() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RegisterNotificationEvent useCase = new RegisterNotificationEvent(events, attempts, subscriptions, clock);

        EventId eventId = new EventId("evt-456");
        ClientId clientId = new ClientId("client-2");
        EventKey eventKey = new EventKey("user.deleted");
        String content = "User deleted";
        Instant occurredAt = Instant.parse("2025-01-01T11:50:00Z");

        RegisterEventCommand command = new RegisterEventCommand(eventId, clientId, eventKey, content, occurredAt);

        when(subscriptions.findActive(clientId, eventKey)).thenReturn(Optional.empty());
        when(events.existsById(eventId)).thenReturn(false);

        RegistrationResult result = useCase.register(command);

        assertThat(result).isEqualTo(RegistrationResult.SKIPPED);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(eventCaptor.capture());

        NotificationEvent saved = eventCaptor.getValue();
        assertThat(saved.status()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(saved.subscriptionId()).isEmpty();
        assertThat(saved.createdAt()).isEqualTo(occurredAt);
        assertThat(saved.receivedAt()).isEqualTo(now);

        verify(attempts, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnDuplicateWhenEventExists() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RegisterNotificationEvent useCase = new RegisterNotificationEvent(events, attempts, subscriptions, clock);

        EventId eventId = new EventId("evt-789");
        ClientId clientId = new ClientId("client-3");
        EventKey eventKey = new EventKey("payment.completed");
        String content = "Payment completed";
        Instant occurredAt = Instant.parse("2025-01-01T11:50:00Z");

        RegisterEventCommand command = new RegisterEventCommand(eventId, clientId, eventKey, content, occurredAt);

        when(events.existsById(eventId)).thenReturn(true);

        RegistrationResult result = useCase.register(command);

        assertThat(result).isEqualTo(RegistrationResult.DUPLICATE);

        verify(subscriptions, never()).findActive(clientId, eventKey);
        verify(events, never()).save(org.mockito.ArgumentMatchers.any());
        verify(attempts, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
