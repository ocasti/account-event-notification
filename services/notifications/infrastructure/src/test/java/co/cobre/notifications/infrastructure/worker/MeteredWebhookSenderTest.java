package co.cobre.notifications.infrastructure.worker;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryOutcome;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.domain.WebhookUrl;
import co.cobre.notifications.domain.fixtures.DeliveryAttempts;
import co.cobre.notifications.domain.fixtures.Ids;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import co.cobre.notifications.domain.fixtures.Subscriptions;
import co.cobre.notifications.infrastructure.webhook.HttpWebhookSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeteredWebhookSenderTest {

    @Mock
    private HttpWebhookSender delegate;

    @Mock
    private DeliveryMetrics metrics;

    @InjectMocks
    private MeteredWebhookSender sender;

    private ListAppender<ILoggingEvent> listAppender;

    private final ClientId clientId = new ClientId("CLIENT123");

    private final NotificationEvent testEvent = NotificationEvents.aPendingEvent()
        .withEventId(Ids.EVT_001)
        .withClientId(clientId)
        .withEventKey(new EventKey("account.updated"))
        .build();

    private final Subscription testSubscription = Subscriptions.aSubscription()
        .withClientId(clientId)
        .withEventKeys(Set.of(new EventKey("account.updated")))
        .withUrl(WebhookUrl.of("https://example.test/webhook"))
        .withSignatureKey("sig-key")
        .build();

    private final DeliveryAttempt testAttempt = DeliveryAttempts.anAttempt()
        .withEventId(Ids.EVT_001)
        .build();

    @BeforeEach
    void setUp() {
        listAppender = new ListAppender<>();
        listAppender.start();
        senderLogger().addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        senderLogger().detachAppender(listAppender);
    }

    /**
     * One row per outcome type: the outcome the delegate returns and the latency
     * the decorator must forward to the metrics.
     */
    static Stream<Arguments> outcomesWithLatency() {
        return Stream.of(
            Arguments.of("success", new DeliveryOutcome.Success(200, Duration.ofMillis(100)), Duration.ofMillis(100)),
            Arguments.of("transient failure",
                new DeliveryOutcome.TransientFailure(Optional.of(500), "server error", Duration.ofMillis(250)),
                Duration.ofMillis(250)),
            Arguments.of("permanent failure",
                new DeliveryOutcome.PermanentFailure(400, "bad request", Duration.ofMillis(75)),
                Duration.ofMillis(75))
        );
    }

    /**
     * One row per outcome shape: the outcome the delegate returns and the
     * key-value pairs the structured log line must carry for it. A transient
     * failure without status logs "none" as its status.
     */
    static Stream<Arguments> outcomesWithLogFields() {
        return Stream.of(
            Arguments.of("success",
                new DeliveryOutcome.Success(200, Duration.ofMillis(100)),
                Map.of("outcome", "success", "response_status", 200, "latency_ms", 100L)),
            Arguments.of("transient failure with status",
                new DeliveryOutcome.TransientFailure(Optional.of(500), "server error", Duration.ofMillis(250)),
                Map.of("outcome", "transient_failure", "response_status", 500, "latency_ms", 250L,
                    "reason", "server error")),
            Arguments.of("transient failure without status",
                new DeliveryOutcome.TransientFailure(Optional.empty(), "connection timeout", Duration.ofMillis(150)),
                Map.of("outcome", "transient_failure", "response_status", "none", "latency_ms", 150L,
                    "reason", "connection timeout")),
            Arguments.of("permanent failure",
                new DeliveryOutcome.PermanentFailure(400, "bad request", Duration.ofMillis(75)),
                Map.of("outcome", "permanent_failure", "response_status", 400, "latency_ms", 75L,
                    "reason", "bad request"))
        );
    }

    @ParameterizedTest(name = "{0} forwards a latency of {2}")
    @MethodSource("outcomesWithLatency")
    void shouldRecordLatencyAndReturnOutcomeWhenDelegateResponds(
        String scenario, DeliveryOutcome outcome, Duration expectedLatency
    ) {
        when(delegate.send(testSubscription, testEvent, testAttempt)).thenReturn(outcome);

        var result = sender.send(testSubscription, testEvent, testAttempt);

        assertThat(result).as(scenario).isEqualTo(outcome);
        verify(metrics).webhookLatency("CLIENT123", expectedLatency);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void shouldPropagateExceptionWithoutRecordingLatencyWhenDelegateThrows() {
        when(delegate.send(testSubscription, testEvent, testAttempt))
            .thenThrow(new RuntimeException("network error"));

        assertThatThrownBy(() -> sender.send(testSubscription, testEvent, testAttempt))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("network error");

        verifyNoInteractions(metrics);
    }

    @ParameterizedTest(name = "{0} logs its outcome fields")
    @MethodSource("outcomesWithLogFields")
    void shouldLogStructuredAttemptWhenDelegateResponds(
        String scenario, DeliveryOutcome outcome, Map<String, Object> expectedFields
    ) {
        when(delegate.send(testSubscription, testEvent, testAttempt)).thenReturn(outcome);

        sender.send(testSubscription, testEvent, testAttempt);

        assertThat(listAppender.list).as(scenario).hasSize(1);
        var logged = listAppender.list.get(0);
        assertThat(logged.getMessage()).isEqualTo("webhook delivery attempt");
        assertThat(keyValuesOf(logged))
            .containsEntry("event_id", "EVT001")
            .containsEntry("client_id", "CLIENT123")
            .containsEntry("cycle", 0)
            .containsEntry("attempt_number", 1)
            .containsAllEntriesOf(expectedFields);
    }

    @Test
    void shouldOmitReasonFromLogWhenOutcomeIsSuccess() {
        var outcome = new DeliveryOutcome.Success(200, Duration.ofMillis(100));
        when(delegate.send(testSubscription, testEvent, testAttempt)).thenReturn(outcome);

        sender.send(testSubscription, testEvent, testAttempt);

        assertThat(listAppender.list).hasSize(1);
        assertThat(keyValuesOf(listAppender.list.get(0))).doesNotContainKey("reason");
    }

    private static Logger senderLogger() {
        return (Logger) LoggerFactory.getLogger(MeteredWebhookSender.class);
    }

    private static Map<String, Object> keyValuesOf(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
            .collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
    }
}
