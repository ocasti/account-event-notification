package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.port.SubscriptionRepository;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.Ids;
import co.cobre.notifications.domain.fixtures.Subscriptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterNotificationEventTest {
    @Mock
    NotificationEventRepository events;
    @Mock
    DeliveryAttemptRepository attempts;
    @Mock
    SubscriptionRepository subscriptions;

    private static final Instant OCCURRED_AT = Clocks.NOW.minusSeconds(600);

    @Test
    void shouldRegisterEventWhenActiveSubscriptionExists() {
        var useCase = new RegisterNotificationEvent(events, attempts, subscriptions, Clocks.fixed());
        Subscription subscription = Subscriptions.activeFor(Ids.CLIENT_001);
        var command = new RegisterEventCommand(
            Ids.EVT_001, Ids.CLIENT_001, Ids.CREDIT_CARD_PAYMENT, "Credit card payment received for $150.00", OCCURRED_AT
        );
        when(subscriptions.findActive(Ids.CLIENT_001, Ids.CREDIT_CARD_PAYMENT)).thenReturn(Optional.of(subscription));
        when(events.existsById(Ids.EVT_001)).thenReturn(false);

        RegistrationResult result = useCase.register(command);

        assertThat(result).isEqualTo(RegistrationResult.REGISTERED);
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(eventCaptor.capture());
        NotificationEvent saved = eventCaptor.getValue();
        assertThat(saved.eventId()).isEqualTo(Ids.EVT_001);
        assertThat(saved.clientId()).isEqualTo(Ids.CLIENT_001);
        assertThat(saved.eventKey()).isEqualTo(Ids.CREDIT_CARD_PAYMENT);
        assertThat(saved.content()).isEqualTo("Credit card payment received for $150.00");
        assertThat(saved.createdAt()).isEqualTo(OCCURRED_AT);
        assertThat(saved.receivedAt()).isEqualTo(Clocks.NOW);
        assertThat(saved.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(saved.subscriptionId()).contains(subscription.id());
        assertThat(saved.cycle()).isZero();
        ArgumentCaptor<DeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attempts).save(attemptCaptor.capture());
        DeliveryAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.eventId()).isEqualTo(Ids.EVT_001);
        assertThat(attempt.cycle()).isZero();
        assertThat(attempt.attemptNumber()).isEqualTo(1);
        assertThat(attempt.nextAttemptAt()).isEqualTo(Clocks.NOW);
        assertThat(attempt.origin()).isEqualTo(AttemptOrigin.SYSTEM);
    }

    @Test
    void shouldSkipEventWhenNoActiveSubscriptionExists() {
        var useCase = new RegisterNotificationEvent(events, attempts, subscriptions, Clocks.fixed());
        var command = new RegisterEventCommand(
            Ids.EVT_003, Ids.CLIENT_002, Ids.CREDIT_TRANSFER, "Bank transfer received from Account #4567 for $1,500.00", OCCURRED_AT
        );
        when(subscriptions.findActive(Ids.CLIENT_002, Ids.CREDIT_TRANSFER)).thenReturn(Optional.empty());
        when(events.existsById(Ids.EVT_003)).thenReturn(false);

        RegistrationResult result = useCase.register(command);

        assertThat(result).isEqualTo(RegistrationResult.SKIPPED);
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(eventCaptor.capture());
        NotificationEvent saved = eventCaptor.getValue();
        assertThat(saved.status()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(saved.subscriptionId()).isEmpty();
        assertThat(saved.createdAt()).isEqualTo(OCCURRED_AT);
        assertThat(saved.receivedAt()).isEqualTo(Clocks.NOW);
        verifyNoInteractions(attempts);
    }

    @Test
    void shouldReturnDuplicateWhenEventAlreadyExists() {
        var useCase = new RegisterNotificationEvent(events, attempts, subscriptions, Clocks.fixed());
        var command = new RegisterEventCommand(
            Ids.EVT_001, Ids.CLIENT_001, Ids.CREDIT_CARD_PAYMENT, "Credit card payment received for $150.00", OCCURRED_AT
        );
        when(events.existsById(Ids.EVT_001)).thenReturn(true);

        RegistrationResult result = useCase.register(command);

        assertThat(result).isEqualTo(RegistrationResult.DUPLICATE);
        verify(events).existsById(Ids.EVT_001);
        verifyNoMoreInteractions(events);
        verifyNoInteractions(subscriptions);
        verifyNoInteractions(attempts);
    }
}
