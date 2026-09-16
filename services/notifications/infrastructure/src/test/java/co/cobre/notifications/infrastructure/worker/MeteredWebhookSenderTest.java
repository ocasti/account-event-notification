package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.port.WebhookSender;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryOutcome;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.domain.WebhookUrl;
import co.cobre.notifications.infrastructure.webhook.HttpWebhookSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeteredWebhookSenderTest {

    @Mock
    private HttpWebhookSender delegate;

    @Mock
    private DeliveryMetrics metrics;

    @InjectMocks
    private MeteredWebhookSender sender;

    private final ClientId clientId = new ClientId("CLIENT123");

    private final NotificationEvent testEvent = new NotificationEvent(
        new EventId("EVT001"),
        clientId,
        new EventKey("account.updated"),
        "content",
        Instant.parse("2025-01-01T10:00:00Z"),
        Instant.parse("2025-01-01T10:00:00Z"),
        DeliveryStatus.PENDING,
        Optional.of("SUB001"),
        0,
        Optional.empty()
    );

    private final Subscription testSubscription = new Subscription(
        "SUB001",
        clientId,
        Set.of(new EventKey("account.updated")),
        WebhookUrl.of("https://example.test/webhook"),
        Optional.empty(),
        Optional.of("sig-key"),
        true,
        Instant.parse("2025-01-01T10:00:00Z")
    );

    private final DeliveryAttempt testAttempt = DeliveryAttempt.first(
        new EventId("EVT001"),
        0,
        Instant.parse("2025-01-01T10:00:00Z"),
        AttemptOrigin.SYSTEM
    );

    @Test
    void sendSuccessRecordsLatency() {
        var outcome = new DeliveryOutcome.Success(200, Duration.ofMillis(100));
        when(delegate.send(testSubscription, testEvent, testAttempt)).thenReturn(outcome);

        var result = sender.send(testSubscription, testEvent, testAttempt);

        assertEquals(outcome, result);
        verify(metrics).webhookLatency("CLIENT123", Duration.ofMillis(100));
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void sendTransientFailureRecordsLatency() {
        var latency = Duration.ofMillis(250);
        var outcome = new DeliveryOutcome.TransientFailure(Optional.of(500), "server error", latency);
        when(delegate.send(testSubscription, testEvent, testAttempt)).thenReturn(outcome);

        var result = sender.send(testSubscription, testEvent, testAttempt);

        assertEquals(outcome, result);
        verify(metrics).webhookLatency("CLIENT123", latency);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void sendPermanentFailureRecordsLatency() {
        var latency = Duration.ofMillis(75);
        var outcome = new DeliveryOutcome.PermanentFailure(400, "bad request", latency);
        when(delegate.send(testSubscription, testEvent, testAttempt)).thenReturn(outcome);

        var result = sender.send(testSubscription, testEvent, testAttempt);

        assertEquals(outcome, result);
        verify(metrics).webhookLatency("CLIENT123", latency);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void sendDelegateThrowsDoesNotRecordLatency() {
        when(delegate.send(testSubscription, testEvent, testAttempt))
            .thenThrow(new RuntimeException("network error"));

        assertThrows(RuntimeException.class, () -> sender.send(testSubscription, testEvent, testAttempt));
        verifyNoInteractions(metrics);
    }
}
