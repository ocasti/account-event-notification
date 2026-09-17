package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryClaim;
import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.port.SubscriptionRepository;
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
import co.cobre.notifications.domain.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessDueDeliveriesTest {
    @Mock
    NotificationEventRepository events;
    @Mock
    DeliveryAttemptRepository attempts;
    @Mock
    SubscriptionRepository subscriptions;
    @Mock
    WebhookSender sender;

    private RandomGenerator fixedRandom(double value) {
        RandomGenerator random = mock(RandomGenerator.class);
        lenient().when(random.nextDouble()).thenReturn(value);
        return random;
    }

    private ProcessDueDeliveries createUseCase(Clock clock, RandomGenerator random, Executor executor) {
        var settings = new DeliveryWorkerSettings("worker-1", 20, 5, Duration.ofSeconds(16));
        return new ProcessDueDeliveries(
            events, attempts, subscriptions, sender,
            RetryPolicy.standard(),
            random,
            clock,
            settings,
            executor
        );
    }

    private DeliveryAttempt attempt(EventId eventId, int attemptNumber, Instant dueAt) {
        return new DeliveryAttempt(
            UUID.randomUUID(),
            eventId,
            0,
            attemptNumber,
            dueAt,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            AttemptOrigin.SYSTEM
        );
    }

    private List<DeliveryAttempt> dueAttempts(Instant now, String... eventIds) {
        return Arrays.stream(eventIds)
            .map(id -> attempt(new EventId(id), 1, now.minusSeconds(60)))
            .toList();
    }

    private NotificationEvent orderEvent(EventId eventId, ClientId clientId, DeliveryStatus status, String subscriptionId) {
        return new NotificationEvent(
            eventId,
            clientId,
            new EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z"),
            status,
            Optional.of(subscriptionId),
            0,
            Optional.empty()
        );
    }

    private Subscription activeSubscription(String subscriptionId, ClientId clientId) {
        return new Subscription(
            subscriptionId,
            clientId,
            Set.of(new EventKey("order.created")),
            WebhookUrl.of("https://example.com/webhook"),
            Optional.empty(),
            Optional.empty(),
            true,
            Instant.parse("2025-01-01T10:00:00Z")
        );
    }

    private void stubSuccessfulDeliveryForEachAttempt(List<DeliveryAttempt> attemptsList, ClientId clientId, String subscriptionId) {
        attemptsList.forEach(attempt -> {
            var event = orderEvent(attempt.eventId(), clientId, DeliveryStatus.PENDING, subscriptionId);
            var subscription = activeSubscription(subscriptionId, clientId);
            lenient().when(events.findById(attempt.eventId())).thenReturn(Optional.of(event));
            lenient().when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
            lenient().when(sender.send(subscription, event, attempt))
                .thenReturn(new DeliveryOutcome.Success(200, Duration.ofMillis(50)));
            lenient().when(this.attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1"))).thenReturn(true);
            lenient().when(this.events.transition(eq(attempt.eventId()), eq(DeliveryStatus.PENDING), any(NotificationEvent.class))).thenReturn(true);
        });
    }

    private void stubMixedDeliveryOutcomes(List<DeliveryAttempt> attemptsList, ClientId clientId, String subscriptionId, EventId failingEventId) {
        attemptsList.forEach(attempt -> {
            var event = orderEvent(attempt.eventId(), clientId, DeliveryStatus.PENDING, subscriptionId);
            var subscription = activeSubscription(subscriptionId, clientId);
            lenient().when(events.findById(attempt.eventId())).thenReturn(Optional.of(event));
            lenient().when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
            boolean isFailingAttempt = attempt.eventId().equals(failingEventId);
            lenient().when(sender.send(subscription, event, attempt))
                .thenAnswer(invocation -> {
                    if (isFailingAttempt) {
                        throw new RuntimeException("webhook sender exploded");
                    }
                    return new DeliveryOutcome.Success(200, Duration.ofMillis(50));
                });
            lenient().when(this.attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1"))).thenReturn(true);
            lenient().when(this.events.transition(eq(attempt.eventId()), eq(DeliveryStatus.PENDING), any(NotificationEvent.class))).thenReturn(true);
        });
    }

    @Test
    void shouldClaimAndProcessBatchWhenAttemptsAreDue() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);
        ProcessDueDeliveries useCase = createUseCase(clock, random, Runnable::run);
        EventId eventId = new EventId("evt-123");
        DeliveryAttempt attempt = attempt(eventId, 1, now.minusSeconds(60));
        var claim = new DeliveryClaim(now, 20, 5, "worker-1", Duration.ofSeconds(16));
        when(attempts.claimDue(claim)).thenReturn(List.of(attempt));

        int count = useCase.processBatch();

        assertThat(count).isEqualTo(1);
        verify(attempts).claimDue(claim);
    }

    @Test
    void shouldCompleteDeliveryWhenSenderSucceeds() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);
        ProcessDueDeliveries useCase = createUseCase(clock, random, Runnable::run);
        EventId eventId = new EventId("evt-success");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";
        DeliveryAttempt attempt = attempt(eventId, 1, now.minusSeconds(60));
        NotificationEvent event = orderEvent(eventId, clientId, DeliveryStatus.PENDING, subscriptionId);
        Subscription subscription = activeSubscription(subscriptionId, clientId);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, attempt)).thenReturn(new DeliveryOutcome.Success(200, Duration.ofMillis(120)));
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1"))).thenReturn(true);
        when(events.transition(eq(eventId), eq(DeliveryStatus.PENDING), any(NotificationEvent.class))).thenReturn(true);

        useCase.process(attempt);

        ArgumentCaptor<DeliveryAttempt> executedCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attempts).recordResultIf(executedCaptor.capture(), eq("worker-1"));
        DeliveryAttempt executed = executedCaptor.getValue();
        assertThat(executed.executedAt()).isPresent().contains(now);
        assertThat(executed.responseStatus()).isPresent().contains(200);
        assertThat(executed.latency()).isPresent().contains(Duration.ofMillis(120));
        assertThat(executed.claimedBy()).isPresent().contains("worker-1");
        ArgumentCaptor<NotificationEvent> completedCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).transition(eq(eventId), eq(DeliveryStatus.PENDING), completedCaptor.capture());
        NotificationEvent updated = completedCaptor.getValue();
        assertThat(updated.status()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(updated.deliveredAt()).isPresent().contains(now);
    }

    @Test
    void shouldScheduleRetryWhenTransientFailureHasBudget() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);
        ProcessDueDeliveries useCase = createUseCase(clock, random, Runnable::run);
        EventId eventId = new EventId("evt-retry");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";
        DeliveryAttempt attempt2 = attempt(eventId, 2, now.minusSeconds(60));
        NotificationEvent event = orderEvent(eventId, clientId, DeliveryStatus.RETRYING, subscriptionId);
        Subscription subscription = activeSubscription(subscriptionId, clientId);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, attempt2))
            .thenReturn(new DeliveryOutcome.TransientFailure(Optional.of(503), "Service unavailable", Duration.ofMillis(50)));
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1"))).thenReturn(true);
        when(events.transition(eq(eventId), eq(DeliveryStatus.RETRYING), any(NotificationEvent.class))).thenReturn(true);

        useCase.process(attempt2);

        verify(attempts).save(any(DeliveryAttempt.class));
        ArgumentCaptor<NotificationEvent> retryingCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).transition(eq(eventId), eq(DeliveryStatus.RETRYING), retryingCaptor.capture());
        NotificationEvent updated = retryingCaptor.getValue();
        assertThat(updated.status()).isEqualTo(DeliveryStatus.RETRYING);
    }

    static Stream<Arguments> terminalFailureCases() {
        return Stream.of(
            Arguments.of(
                "retry budget exhausted after transient failure",
                5,
                DeliveryStatus.RETRYING,
                new DeliveryOutcome.TransientFailure(Optional.of(503), "Service unavailable", Duration.ofMillis(50))
            ),
            Arguments.of(
                "permanent failure on first attempt",
                1,
                DeliveryStatus.PENDING,
                new DeliveryOutcome.PermanentFailure(404, "Not found", Duration.ofMillis(50))
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("terminalFailureCases")
    void shouldFailEventWhenRetryBudgetOrFailureIsExhausted(String caseLabel, int attemptNumber, DeliveryStatus originalStatus, DeliveryOutcome outcome) {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);
        ProcessDueDeliveries useCase = createUseCase(clock, random, Runnable::run);
        EventId eventId = new EventId("evt-terminal");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";
        DeliveryAttempt terminalAttempt = attempt(eventId, attemptNumber, now.minusSeconds(60));
        NotificationEvent event = orderEvent(eventId, clientId, originalStatus, subscriptionId);
        Subscription subscription = activeSubscription(subscriptionId, clientId);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, terminalAttempt)).thenReturn(outcome);
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1"))).thenReturn(true);
        when(events.transition(eq(eventId), eq(originalStatus), any(NotificationEvent.class))).thenReturn(true);

        useCase.process(terminalAttempt);

        ArgumentCaptor<NotificationEvent> failedCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(attempts, never()).save(any(DeliveryAttempt.class));
        verify(events).transition(eq(eventId), eq(originalStatus), failedCaptor.capture());
        assertThat(failedCaptor.getValue().status()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void shouldSkipTransitionWhenRecordResultFails() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);
        ProcessDueDeliveries useCase = createUseCase(clock, random, Runnable::run);
        EventId eventId = new EventId("evt-record-fail");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";
        DeliveryAttempt attempt = attempt(eventId, 1, now.minusSeconds(60));
        NotificationEvent event = orderEvent(eventId, clientId, DeliveryStatus.PENDING, subscriptionId);
        Subscription subscription = activeSubscription(subscriptionId, clientId);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, attempt)).thenReturn(new DeliveryOutcome.Success(200, Duration.ofMillis(120)));
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1"))).thenReturn(false);

        useCase.process(attempt);

        verify(events, never()).transition(any(), any(), any());
    }

    @Test
    void shouldFailWhenSubscriptionUnavailable() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);
        ProcessDueDeliveries useCase = createUseCase(clock, random, Runnable::run);
        EventId eventId = new EventId("evt-no-sub");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-missing";
        DeliveryAttempt attempt = attempt(eventId, 1, now.minusSeconds(60));
        NotificationEvent event = orderEvent(eventId, clientId, DeliveryStatus.PENDING, subscriptionId);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.empty());
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1"))).thenReturn(true);
        when(events.transition(eq(eventId), eq(DeliveryStatus.PENDING), any(NotificationEvent.class))).thenReturn(true);

        useCase.process(attempt);

        verify(sender, never()).send(any(), any(), any());
        ArgumentCaptor<DeliveryAttempt> executedCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attempts).recordResultIf(executedCaptor.capture(), eq("worker-1"));
        DeliveryAttempt executed = executedCaptor.getValue();
        assertThat(executed.failureReason()).isPresent();
        ArgumentCaptor<NotificationEvent> failedCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).transition(eq(eventId), eq(DeliveryStatus.PENDING), failedCaptor.capture());
        NotificationEvent updated = failedCaptor.getValue();
        assertThat(updated.status()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void shouldProcessAllAttemptsWhenBatchRunsInParallel() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);
        Executor executor = Executors.newFixedThreadPool(2);
        ProcessDueDeliveries useCase = createUseCase(clock, random, executor);
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";
        List<DeliveryAttempt> attemptsList = dueAttempts(now, "evt-1", "evt-2", "evt-3", "evt-4", "evt-5");
        var claim = new DeliveryClaim(now, 20, 5, "worker-1", Duration.ofSeconds(16));
        when(attempts.claimDue(claim)).thenReturn(attemptsList);
        stubSuccessfulDeliveryForEachAttempt(attemptsList, clientId, subscriptionId);

        int count = useCase.processBatch();

        assertThat(count).isEqualTo(5);
        verify(sender, times(5)).send(any(Subscription.class), any(NotificationEvent.class), any(DeliveryAttempt.class));
    }

    @Test
    void shouldPropagateFailureWhenOneAttemptThrowsDuringBatch() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);
        Executor executor = Executors.newFixedThreadPool(3);
        ProcessDueDeliveries useCase = createUseCase(clock, random, executor);
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";
        EventId eventId1 = new EventId("evt-ok-1");
        EventId eventId2 = new EventId("evt-boom");
        EventId eventId3 = new EventId("evt-ok-2");
        List<DeliveryAttempt> attemptsList = dueAttempts(now, "evt-ok-1", "evt-boom", "evt-ok-2");
        var claim = new DeliveryClaim(now, 20, 5, "worker-1", Duration.ofSeconds(16));
        when(attempts.claimDue(claim)).thenReturn(attemptsList);
        stubMixedDeliveryOutcomes(attemptsList, clientId, subscriptionId, eventId2);

        assertThatThrownBy(useCase::processBatch)
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(RuntimeException.class)
            .cause().hasMessage("webhook sender exploded");

        verify(sender, times(3)).send(any(Subscription.class), any(NotificationEvent.class), any(DeliveryAttempt.class));
        verify(attempts).recordResultIf(argThat(a -> a.eventId().equals(eventId1)), eq("worker-1"));
        verify(attempts).recordResultIf(argThat(a -> a.eventId().equals(eventId3)), eq("worker-1"));
    }
}
