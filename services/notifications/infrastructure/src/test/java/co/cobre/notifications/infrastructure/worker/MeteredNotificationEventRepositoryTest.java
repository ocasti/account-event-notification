package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import co.cobre.notifications.infrastructure.persistence.NotificationEventRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeteredNotificationEventRepositoryTest {

    @Mock
    private NotificationEventRepositoryAdapter delegate;

    @Mock
    private DeliveryMetrics metrics;

    @InjectMocks
    private MeteredNotificationEventRepository repository;

    private final ClientId clientId = new ClientId("CLIENT123");
    private final EventId eventId = new EventId("EVT001");
    private final EventKey eventKey = new EventKey("account.updated");

    @ParameterizedTest(name = "transition to {0} records the delivered metric as {1}")
    @CsvSource({
        "COMPLETED, completed",
        "RETRYING, retrying",
        "FAILED, failed"
    })
    void shouldRecordDeliveredMetricWhenTransitionToOutcomeStatusSucceeds(DeliveryStatus target, String metricStatus) {
        var updated = eventWithStatus(target);
        when(delegate.transition(eventId, DeliveryStatus.PENDING, updated)).thenReturn(true);

        boolean result = repository.transition(eventId, DeliveryStatus.PENDING, updated);

        assertThat(result).isTrue();
        verify(delegate).transition(eventId, DeliveryStatus.PENDING, updated);
        verify(metrics).delivered("CLIENT123", metricStatus, "account.updated");
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void shouldNotRecordMetricWhenTransitionTargetIsPending() {
        var pendingEvent = eventWithStatus(DeliveryStatus.PENDING);
        when(delegate.transition(eventId, DeliveryStatus.FAILED, pendingEvent)).thenReturn(true);

        boolean result = repository.transition(eventId, DeliveryStatus.FAILED, pendingEvent);

        assertThat(result).isTrue();
        verify(delegate).transition(eventId, DeliveryStatus.FAILED, pendingEvent);
        verifyNoInteractions(metrics);
    }

    @Test
    void shouldNotRecordMetricWhenDelegateRejectsTransition() {
        var completedEvent = eventWithStatus(DeliveryStatus.COMPLETED);
        when(delegate.transition(eventId, DeliveryStatus.PENDING, completedEvent)).thenReturn(false);

        boolean result = repository.transition(eventId, DeliveryStatus.PENDING, completedEvent);

        assertThat(result).isFalse();
        verify(delegate).transition(eventId, DeliveryStatus.PENDING, completedEvent);
        verifyNoInteractions(metrics);
    }

    @Test
    void shouldDelegateWithoutMetricsWhenEventIsSaved() {
        var event = eventWithStatus(DeliveryStatus.PENDING);

        repository.save(event);

        verify(delegate).save(event);
        verifyNoInteractions(metrics);
    }

    @Test
    void shouldDelegateWithoutMetricsWhenEventIsFoundById() {
        var event = eventWithStatus(DeliveryStatus.PENDING);
        when(delegate.findById(eventId)).thenReturn(Optional.of(event));

        var result = repository.findById(eventId);

        assertThat(result).contains(event);
        verify(delegate).findById(eventId);
        verifyNoInteractions(metrics);
    }

    private NotificationEvent eventWithStatus(DeliveryStatus status) {
        return NotificationEvents.aPendingEvent()
            .withEventId(eventId)
            .withClientId(clientId)
            .withEventKey(eventKey)
            .withStatus(status)
            .build();
    }
}
