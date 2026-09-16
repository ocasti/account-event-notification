package co.cobre.notifications.infrastructure.worker;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        listAppender = new ListAppender<>();
        listAppender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(MeteredWebhookSender.class);
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(MeteredWebhookSender.class);
        logger.detachAppender(listAppender);
    }

    /**
     * Converts a list of KeyValuePair to a Map<String, Object> for easier assertion.
     */
    private Map<String, Object> kvListToMap(java.util.List<org.slf4j.event.KeyValuePair> kvList) {
        Map<String, Object> map = new HashMap<>();
        for (var kv : kvList) {
            map.put(kv.key, kv.value);
        }
        return map;
    }

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

    @Test
    void sendSuccessLogsStructuredEvent() {
        var outcome = new DeliveryOutcome.Success(200, Duration.ofMillis(100));
        when(delegate.send(testSubscription, testEvent, testAttempt)).thenReturn(outcome);

        sender.send(testSubscription, testEvent, testAttempt);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals("webhook delivery attempt", event.getMessage());

        var kvPairs = kvListToMap(event.getKeyValuePairs());
        assertEquals("EVT001", kvPairs.get("event_id"));
        assertEquals("CLIENT123", kvPairs.get("client_id"));
        assertEquals(0, (int) kvPairs.get("cycle"));
        assertEquals(1, (int) kvPairs.get("attempt_number"));
        assertEquals("success", kvPairs.get("outcome"));
        assertEquals(200, (int) kvPairs.get("response_status"));
        assertEquals(100L, (long) kvPairs.get("latency_ms"));
    }

    @Test
    void sendTransientFailureWithStatusLogsStructuredEvent() {
        var latency = Duration.ofMillis(250);
        var outcome = new DeliveryOutcome.TransientFailure(Optional.of(500), "server error", latency);
        when(delegate.send(testSubscription, testEvent, testAttempt)).thenReturn(outcome);

        sender.send(testSubscription, testEvent, testAttempt);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);

        var kvPairs = kvListToMap(event.getKeyValuePairs());
        assertEquals("EVT001", kvPairs.get("event_id"));
        assertEquals("CLIENT123", kvPairs.get("client_id"));
        assertEquals(0, (int) kvPairs.get("cycle"));
        assertEquals(1, (int) kvPairs.get("attempt_number"));
        assertEquals("transient_failure", kvPairs.get("outcome"));
        assertEquals(500, (int) kvPairs.get("response_status"));
        assertEquals(250L, (long) kvPairs.get("latency_ms"));
        assertEquals("server error", (String) kvPairs.get("reason"));
    }

    @Test
    void sendTransientFailureWithoutStatusLogsStructuredEvent() {
        var latency = Duration.ofMillis(150);
        var outcome = new DeliveryOutcome.TransientFailure(Optional.empty(), "connection timeout", latency);
        when(delegate.send(testSubscription, testEvent, testAttempt)).thenReturn(outcome);

        sender.send(testSubscription, testEvent, testAttempt);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);

        var kvPairs = kvListToMap(event.getKeyValuePairs());
        assertEquals("EVT001", kvPairs.get("event_id"));
        assertEquals("CLIENT123", kvPairs.get("client_id"));
        assertEquals(0, (int) kvPairs.get("cycle"));
        assertEquals(1, (int) kvPairs.get("attempt_number"));
        assertEquals("transient_failure", kvPairs.get("outcome"));
        assertEquals("none", (String) kvPairs.get("response_status"));
        assertEquals(150L, (long) kvPairs.get("latency_ms"));
        assertEquals("connection timeout", (String) kvPairs.get("reason"));
    }

    @Test
    void sendPermanentFailureLogsStructuredEvent() {
        var latency = Duration.ofMillis(75);
        var outcome = new DeliveryOutcome.PermanentFailure(400, "bad request", latency);
        when(delegate.send(testSubscription, testEvent, testAttempt)).thenReturn(outcome);

        sender.send(testSubscription, testEvent, testAttempt);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);

        var kvPairs = kvListToMap(event.getKeyValuePairs());
        assertEquals("EVT001", kvPairs.get("event_id"));
        assertEquals("CLIENT123", kvPairs.get("client_id"));
        assertEquals(0, (int) kvPairs.get("cycle"));
        assertEquals(1, (int) kvPairs.get("attempt_number"));
        assertEquals("permanent_failure", kvPairs.get("outcome"));
        assertEquals(400, (int) kvPairs.get("response_status"));
        assertEquals(75L, (long) kvPairs.get("latency_ms"));
        assertEquals("bad request", (String) kvPairs.get("reason"));
    }
}
