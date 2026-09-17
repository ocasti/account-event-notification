package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.fixtures.DeliveryAttempts;
import co.cobre.notifications.domain.fixtures.Ids;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void shouldReturnEventDetailWhenAttemptsExist() {
        GetNotificationEvent useCase = new GetNotificationEvent(events, attempts);
        NotificationEvent event = NotificationEvents.pending();
        var recordedAttempt = DeliveryAttempts.due();
        when(events.findByClientAndId(Ids.CLIENT_001, Ids.EVT_001)).thenReturn(Optional.of(event));
        when(attempts.findByEvent(Ids.EVT_001)).thenReturn(List.of(recordedAttempt));

        NotificationEventDetail result = useCase.get(Ids.CLIENT_001, Ids.EVT_001);

        assertThat(result.event()).isEqualTo(event);
        assertThat(result.attempts()).containsExactly(recordedAttempt);
        verify(events).findByClientAndId(Ids.CLIENT_001, Ids.EVT_001);
        verify(attempts).findByEvent(Ids.EVT_001);
    }

    @Test
    void shouldThrowWhenEventDoesNotExistForClient() {
        GetNotificationEvent useCase = new GetNotificationEvent(events, attempts);
        when(events.findByClientAndId(Ids.CLIENT_001, Ids.EVT_001)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(Ids.CLIENT_001, Ids.EVT_001))
            .isInstanceOf(NotificationEventNotFoundException.class);

        verify(attempts, never()).findByEvent(Ids.EVT_001);
    }
}
