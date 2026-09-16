package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.infrastructure.persistence.NotificationEventRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test
    void transitionToCompletedRecordsMetric() {
        var completedEvent = new NotificationEvent(
            eventId,
            clientId,
            eventKey,
            "content",
            Instant.parse("2025-01-01T10:00:00Z"),
            Instant.parse("2025-01-01T10:00:00Z"),
            DeliveryStatus.COMPLETED,
            Optional.of("SUB001"),
            0,
            Optional.of(Instant.parse("2025-01-01T10:05:00Z"))
        );

        when(delegate.transition(eventId, DeliveryStatus.PENDING, completedEvent)).thenReturn(true);

        boolean result = repository.transition(eventId, DeliveryStatus.PENDING, completedEvent);

        assertTrue(result);
        verify(metrics).delivered("CLIENT123", "completed", "account.updated");
        verify(delegate).transition(eventId, DeliveryStatus.PENDING, completedEvent);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void transitionToRetryingRecordsMetric() {
        var retryingEvent = new NotificationEvent(
            eventId,
            clientId,
            eventKey,
            "content",
            Instant.parse("2025-01-01T10:00:00Z"),
            Instant.parse("2025-01-01T10:00:00Z"),
            DeliveryStatus.RETRYING,
            Optional.of("SUB001"),
            0,
            Optional.empty()
        );

        when(delegate.transition(eventId, DeliveryStatus.PENDING, retryingEvent)).thenReturn(true);

        boolean result = repository.transition(eventId, DeliveryStatus.PENDING, retryingEvent);

        assertTrue(result);
        verify(metrics).delivered("CLIENT123", "retrying", "account.updated");
        verify(delegate).transition(eventId, DeliveryStatus.PENDING, retryingEvent);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void transitionToFailedRecordsMetric() {
        var failedEvent = new NotificationEvent(
            eventId,
            clientId,
            eventKey,
            "content",
            Instant.parse("2025-01-01T10:00:00Z"),
            Instant.parse("2025-01-01T10:00:00Z"),
            DeliveryStatus.FAILED,
            Optional.of("SUB001"),
            0,
            Optional.empty()
        );

        when(delegate.transition(eventId, DeliveryStatus.PENDING, failedEvent)).thenReturn(true);

        boolean result = repository.transition(eventId, DeliveryStatus.PENDING, failedEvent);

        assertTrue(result);
        verify(metrics).delivered("CLIENT123", "failed", "account.updated");
        verify(delegate).transition(eventId, DeliveryStatus.PENDING, failedEvent);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void transitionToPendingDoesNotRecordMetric() {
        var pendingEvent = new NotificationEvent(
            eventId,
            clientId,
            eventKey,
            "content",
            Instant.parse("2025-01-01T10:00:00Z"),
            Instant.parse("2025-01-01T10:00:00Z"),
            DeliveryStatus.PENDING,
            Optional.of("SUB001"),
            1,
            Optional.empty()
        );

        when(delegate.transition(eventId, DeliveryStatus.FAILED, pendingEvent)).thenReturn(true);

        boolean result = repository.transition(eventId, DeliveryStatus.FAILED, pendingEvent);

        assertTrue(result);
        verify(delegate).transition(eventId, DeliveryStatus.FAILED, pendingEvent);
        verifyNoInteractions(metrics);
    }

    @Test
    void transitionFailsDoesNotRecordMetric() {
        var completedEvent = new NotificationEvent(
            eventId,
            clientId,
            eventKey,
            "content",
            Instant.parse("2025-01-01T10:00:00Z"),
            Instant.parse("2025-01-01T10:00:00Z"),
            DeliveryStatus.COMPLETED,
            Optional.of("SUB001"),
            0,
            Optional.of(Instant.parse("2025-01-01T10:05:00Z"))
        );

        when(delegate.transition(eventId, DeliveryStatus.PENDING, completedEvent)).thenReturn(false);

        boolean result = repository.transition(eventId, DeliveryStatus.PENDING, completedEvent);

        assertFalse(result);
        verify(delegate).transition(eventId, DeliveryStatus.PENDING, completedEvent);
        verifyNoInteractions(metrics);
    }

    @Test
    void saveDelegates() {
        var event = new NotificationEvent(
            eventId,
            clientId,
            eventKey,
            "content",
            Instant.parse("2025-01-01T10:00:00Z"),
            Instant.parse("2025-01-01T10:00:00Z"),
            DeliveryStatus.PENDING,
            Optional.of("SUB001"),
            0,
            Optional.empty()
        );

        repository.save(event);

        verify(delegate).save(event);
        verifyNoInteractions(metrics);
    }

    @Test
    void findByIdDelegates() {
        var event = new NotificationEvent(
            eventId,
            clientId,
            eventKey,
            "content",
            Instant.parse("2025-01-01T10:00:00Z"),
            Instant.parse("2025-01-01T10:00:00Z"),
            DeliveryStatus.PENDING,
            Optional.of("SUB001"),
            0,
            Optional.empty()
        );

        when(delegate.findById(eventId)).thenReturn(Optional.of(event));

        var result = repository.findById(eventId);

        assertTrue(result.isPresent());
        assertEquals(event, result.get());
        verify(delegate).findById(eventId);
        verifyNoInteractions(metrics);
    }
}
