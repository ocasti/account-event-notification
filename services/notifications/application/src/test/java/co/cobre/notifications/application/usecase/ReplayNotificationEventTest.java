package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.ReplayNotAllowedException;
import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.Ids;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayNotificationEventTest {
    @Mock
    NotificationEventRepository events;
    @Mock
    DeliveryAttemptRepository attempts;

    @Test
    void shouldReplayEventWhenStatusIsFailed() {
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, Clocks.fixed());
        NotificationEvent failedEvent = NotificationEvents.aPendingEvent()
            .withStatus(DeliveryStatus.FAILED)
            .withCycle(2)
            .build();
        when(events.findByClientAndId(Ids.CLIENT_001, Ids.EVT_001)).thenReturn(Optional.of(failedEvent));
        when(events.transition(Ids.EVT_001, DeliveryStatus.FAILED, failedEvent)).thenReturn(true);

        ReplayResult result = useCase.replay(Ids.CLIENT_001, Ids.EVT_001);

        assertThat(result.eventId()).isEqualTo(Ids.EVT_001);
        assertThat(result.cycle()).isEqualTo(3);
        assertThat(failedEvent.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(failedEvent.cycle()).isEqualTo(3);
        verify(events).transition(Ids.EVT_001, DeliveryStatus.FAILED, failedEvent);
        ArgumentCaptor<DeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attempts).save(attemptCaptor.capture());
        DeliveryAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.eventId()).isEqualTo(Ids.EVT_001);
        assertThat(attempt.cycle()).isEqualTo(3);
        assertThat(attempt.attemptNumber()).isEqualTo(1);
        assertThat(attempt.origin()).isEqualTo(AttemptOrigin.REPLAY);
        assertThat(attempt.nextAttemptAt()).isEqualTo(Clocks.NOW);
    }

    @Test
    void shouldThrowWhenTransitionToPendingFails() {
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, Clocks.fixed());
        NotificationEvent failedEvent = NotificationEvents.aPendingEvent()
            .withEventId(Ids.EVT_003)
            .withStatus(DeliveryStatus.FAILED)
            .withCycle(1)
            .build();
        when(events.findByClientAndId(Ids.CLIENT_001, Ids.EVT_003)).thenReturn(Optional.of(failedEvent));
        when(events.transition(Ids.EVT_003, DeliveryStatus.FAILED, failedEvent)).thenReturn(false);

        assertThatThrownBy(() -> useCase.replay(Ids.CLIENT_001, Ids.EVT_003))
            .isInstanceOf(ReplayNotAllowedException.class);

        verifyNoInteractions(attempts);
    }

    @Test
    void shouldThrowWhenEventStatusIsNotFailed() {
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, Clocks.fixed());
        NotificationEvent pendingEvent = NotificationEvents.pending();
        when(events.findByClientAndId(Ids.CLIENT_001, Ids.EVT_001)).thenReturn(Optional.of(pendingEvent));

        assertThatThrownBy(() -> useCase.replay(Ids.CLIENT_001, Ids.EVT_001))
            .isInstanceOf(ReplayNotAllowedException.class);

        verify(events).findByClientAndId(Ids.CLIENT_001, Ids.EVT_001);
        verifyNoMoreInteractions(events);
        verifyNoInteractions(attempts);
    }

    @Test
    void shouldThrowWhenEventNotFoundForReplay() {
        ReplayNotificationEvent useCase = new ReplayNotificationEvent(events, attempts, Clocks.fixed());
        when(events.findByClientAndId(Ids.CLIENT_001, Ids.EVT_001)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.replay(Ids.CLIENT_001, Ids.EVT_001))
            .isInstanceOf(NotificationEventNotFoundException.class);

        verify(events).findByClientAndId(Ids.CLIENT_001, Ids.EVT_001);
        verifyNoMoreInteractions(events);
        verifyNoInteractions(attempts);
    }
}
