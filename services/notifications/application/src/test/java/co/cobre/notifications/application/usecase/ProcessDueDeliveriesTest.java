package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.DeliveryClaim;
import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.port.SubscriptionRepository;
import co.cobre.notifications.application.port.WebhookSender;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryOutcome;
import co.cobre.notifications.domain.DeliveryResult;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.domain.fixtures.Clocks;
import co.cobre.notifications.domain.fixtures.DeliveryAttempts;
import co.cobre.notifications.domain.fixtures.DeliveryOutcomes;
import co.cobre.notifications.domain.fixtures.Ids;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import co.cobre.notifications.domain.fixtures.RetryPolicies;
import co.cobre.notifications.domain.fixtures.Subscriptions;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    private static final String SUBSCRIPTION_ID = "sub-001";

    private RandomGenerator fixedRandom(double value) {
        RandomGenerator random = mock(RandomGenerator.class);
        lenient().when(random.nextDouble()).thenReturn(value);
        return random;
    }

    private DeliveryWorkerSettings settings() {
        return new DeliveryWorkerSettings(Ids.WORKER_1, 20, 5, Duration.ofSeconds(16));
    }

    private ProcessDueDeliveries createUseCase(Clock clock, RandomGenerator random, Executor executor) {
        return new ProcessDueDeliveries(
            events, attempts, subscriptions, sender,
            RetryPolicies.standard(),
            random,
            clock,
            settings(),
            executor
        );
    }

    private DeliveryAttempt dueAttempt(EventId eventId, int attemptNumber) {
        return DeliveryAttempts.anAttempt()
            .withEventId(eventId)
            .withAttemptNumber(attemptNumber)
            .withNextAttemptAt(Clocks.NOW.minusSeconds(60))
            .build();
    }

    private NotificationEvent orderEvent(EventId eventId, DeliveryStatus status, String subscriptionId) {
        return NotificationEvents.aPendingEvent()
            .withEventId(eventId)
            .withStatus(status)
            .withSubscriptionId(subscriptionId)
            .build();
    }

    private Subscription activeSubscription(String subscriptionId) {
        return Subscriptions.aSubscription().withId(subscriptionId).build();
    }

    private NotificationEvent stubSuccessForAttempt(DeliveryAttempt attempt, Subscription subscription) {
        NotificationEvent event = orderEvent(attempt.eventId(), DeliveryStatus.PENDING, subscription.id());
        DeliveryAttempt executedAttempt = attempt.executed(Clocks.NOW, Ids.WORKER_1, DeliveryResult.of(DeliveryOutcomes.success()));
        when(events.findById(attempt.eventId())).thenReturn(Optional.of(event));
        when(sender.send(subscription, event, attempt)).thenReturn(DeliveryOutcomes.success());
        when(attempts.recordResultIf(executedAttempt, Ids.WORKER_1)).thenReturn(true);
        when(events.transition(attempt.eventId(), DeliveryStatus.PENDING, event)).thenReturn(true);
        return event;
    }

    private NotificationEvent stubFailingAttempt(DeliveryAttempt attempt, Subscription subscription) {
        NotificationEvent event = orderEvent(attempt.eventId(), DeliveryStatus.PENDING, subscription.id());
        when(events.findById(attempt.eventId())).thenReturn(Optional.of(event));
        when(sender.send(subscription, event, attempt)).thenThrow(new RuntimeException("webhook sender exploded"));
        return event;
    }

    @Test
    void shouldClaimAndProcessBatchWhenAttemptsAreDue() {
        ProcessDueDeliveries useCase = createUseCase(Clocks.fixed(), fixedRandom(0.5), Runnable::run);
        DeliveryAttempt claimedAttempt = dueAttempt(Ids.EVT_001, 1);
        var claim = new DeliveryClaim(Clocks.NOW, 20, 5, Ids.WORKER_1, Duration.ofSeconds(16));
        when(attempts.claimDue(claim)).thenReturn(List.of(claimedAttempt));

        int count = useCase.processBatch();

        assertThat(count).isEqualTo(1);
        verify(attempts).claimDue(claim);
    }

    @Test
    void shouldCompleteDeliveryWhenSenderSucceeds() {
        ProcessDueDeliveries useCase = createUseCase(Clocks.fixed(), fixedRandom(0.5), Runnable::run);
        DeliveryAttempt attempt = dueAttempt(Ids.EVT_001, 1);
        NotificationEvent event = orderEvent(Ids.EVT_001, DeliveryStatus.PENDING, SUBSCRIPTION_ID);
        Subscription subscription = activeSubscription(SUBSCRIPTION_ID);
        DeliveryAttempt executedAttempt = attempt.executed(Clocks.NOW, Ids.WORKER_1, DeliveryResult.of(DeliveryOutcomes.success()));
        when(events.findById(Ids.EVT_001)).thenReturn(Optional.of(event));
        when(subscriptions.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, attempt)).thenReturn(DeliveryOutcomes.success());
        when(attempts.recordResultIf(executedAttempt, Ids.WORKER_1)).thenReturn(true);
        when(events.transition(Ids.EVT_001, DeliveryStatus.PENDING, event)).thenReturn(true);

        useCase.process(attempt);

        verify(attempts).recordResultIf(executedAttempt, Ids.WORKER_1);
        verify(events).transition(Ids.EVT_001, DeliveryStatus.PENDING, event);
        assertThat(event.status()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(event.deliveredAt()).contains(Clocks.NOW);
    }

    @Test
    void shouldScheduleRetryWhenTransientFailureHasBudget() {
        ProcessDueDeliveries useCase = createUseCase(Clocks.fixed(), fixedRandom(0.5), Runnable::run);
        DeliveryAttempt secondAttempt = dueAttempt(Ids.EVT_001, 2);
        NotificationEvent event = orderEvent(Ids.EVT_001, DeliveryStatus.RETRYING, SUBSCRIPTION_ID);
        Subscription subscription = activeSubscription(SUBSCRIPTION_ID);
        DeliveryOutcome.TransientFailure outcome = DeliveryOutcomes.transientFailure(503);
        DeliveryAttempt executedAttempt = secondAttempt.executed(Clocks.NOW, Ids.WORKER_1, DeliveryResult.of(outcome));
        when(events.findById(Ids.EVT_001)).thenReturn(Optional.of(event));
        when(subscriptions.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, secondAttempt)).thenReturn(outcome);
        when(attempts.recordResultIf(executedAttempt, Ids.WORKER_1)).thenReturn(true);
        when(events.transition(Ids.EVT_001, DeliveryStatus.RETRYING, event)).thenReturn(true);

        useCase.process(secondAttempt);

        ArgumentCaptor<DeliveryAttempt> scheduledCaptor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attempts).save(scheduledCaptor.capture());
        DeliveryAttempt scheduled = scheduledCaptor.getValue();
        assertThat(scheduled.eventId()).isEqualTo(Ids.EVT_001);
        assertThat(scheduled.cycle()).isZero();
        assertThat(scheduled.attemptNumber()).isEqualTo(3);
        assertThat(scheduled.nextAttemptAt()).isEqualTo(Clocks.NOW.plusSeconds(120));
        assertThat(event.status()).isEqualTo(DeliveryStatus.RETRYING);
    }

    static Stream<Arguments> terminalFailureCases() {
        return Stream.of(
            Arguments.of(
                "retry budget exhausted after transient failure",
                5,
                DeliveryStatus.RETRYING,
                DeliveryOutcomes.transientFailure(503)
            ),
            Arguments.of(
                "permanent failure on first attempt",
                1,
                DeliveryStatus.PENDING,
                DeliveryOutcomes.permanentFailure(404)
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("terminalFailureCases")
    void shouldFailEventWhenRetryBudgetOrFailureIsExhausted(
        String caseLabel, int attemptNumber, DeliveryStatus originalStatus, DeliveryOutcome outcome) {

        ProcessDueDeliveries useCase = createUseCase(Clocks.fixed(), fixedRandom(0.5), Runnable::run);
        DeliveryAttempt terminalAttempt = dueAttempt(Ids.EVT_001, attemptNumber);
        NotificationEvent event = orderEvent(Ids.EVT_001, originalStatus, SUBSCRIPTION_ID);
        Subscription subscription = activeSubscription(SUBSCRIPTION_ID);
        DeliveryAttempt executedAttempt = terminalAttempt.executed(Clocks.NOW, Ids.WORKER_1, DeliveryResult.of(outcome));
        when(events.findById(Ids.EVT_001)).thenReturn(Optional.of(event));
        when(subscriptions.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, terminalAttempt)).thenReturn(outcome);
        when(attempts.recordResultIf(executedAttempt, Ids.WORKER_1)).thenReturn(true);
        when(events.transition(Ids.EVT_001, originalStatus, event)).thenReturn(true);

        useCase.process(terminalAttempt);

        verify(attempts).recordResultIf(executedAttempt, Ids.WORKER_1);
        verifyNoMoreInteractions(attempts);
        verify(events).transition(Ids.EVT_001, originalStatus, event);
        assertThat(event.status()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void shouldSkipTransitionWhenRecordResultFails() {
        ProcessDueDeliveries useCase = createUseCase(Clocks.fixed(), fixedRandom(0.5), Runnable::run);
        DeliveryAttempt attempt = dueAttempt(Ids.EVT_001, 1);
        NotificationEvent event = orderEvent(Ids.EVT_001, DeliveryStatus.PENDING, SUBSCRIPTION_ID);
        Subscription subscription = activeSubscription(SUBSCRIPTION_ID);
        DeliveryAttempt executedAttempt = attempt.executed(Clocks.NOW, Ids.WORKER_1, DeliveryResult.of(DeliveryOutcomes.success()));
        when(events.findById(Ids.EVT_001)).thenReturn(Optional.of(event));
        when(subscriptions.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, attempt)).thenReturn(DeliveryOutcomes.success());
        when(attempts.recordResultIf(executedAttempt, Ids.WORKER_1)).thenReturn(false);

        useCase.process(attempt);

        verify(attempts).recordResultIf(executedAttempt, Ids.WORKER_1);
        verifyNoMoreInteractions(attempts);
        verify(events).findById(Ids.EVT_001);
        verifyNoMoreInteractions(events);
    }

    @Test
    void shouldFailWhenSubscriptionUnavailable() {
        ProcessDueDeliveries useCase = createUseCase(Clocks.fixed(), fixedRandom(0.5), Runnable::run);
        DeliveryAttempt attempt = dueAttempt(Ids.EVT_001, 1);
        NotificationEvent event = orderEvent(Ids.EVT_001, DeliveryStatus.PENDING, "sub-missing");
        DeliveryOutcome.PermanentFailure outcome = new DeliveryOutcome.PermanentFailure(0, "subscription unavailable", Duration.ZERO);
        DeliveryAttempt executedAttempt = attempt.executed(Clocks.NOW, Ids.WORKER_1, DeliveryResult.of(outcome));
        when(events.findById(Ids.EVT_001)).thenReturn(Optional.of(event));
        when(subscriptions.findById("sub-missing")).thenReturn(Optional.empty());
        when(attempts.recordResultIf(executedAttempt, Ids.WORKER_1)).thenReturn(true);
        when(events.transition(Ids.EVT_001, DeliveryStatus.PENDING, event)).thenReturn(true);

        useCase.process(attempt);

        verifyNoInteractions(sender);
        verify(attempts).recordResultIf(executedAttempt, Ids.WORKER_1);
        verifyNoMoreInteractions(attempts);
        verify(events).transition(Ids.EVT_001, DeliveryStatus.PENDING, event);
        assertThat(event.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(executedAttempt.failureReason()).contains("subscription unavailable");
    }

    @Test
    void shouldProcessAllAttemptsWhenBatchRunsInParallel() {
        Executor executor = Executors.newFixedThreadPool(2);
        ProcessDueDeliveries useCase = createUseCase(Clocks.fixed(), fixedRandom(0.5), executor);
        List<DeliveryAttempt> attemptsList = DeliveryAttempts.dueBatch(5);
        Subscription subscription = activeSubscription(SUBSCRIPTION_ID);
        when(subscriptions.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(subscription));
        List<NotificationEvent> eventsList = attemptsList.stream()
            .map(attempt -> stubSuccessForAttempt(attempt, subscription))
            .toList();
        var claim = new DeliveryClaim(Clocks.NOW, 20, 5, Ids.WORKER_1, Duration.ofSeconds(16));
        when(attempts.claimDue(claim)).thenReturn(attemptsList);

        int count = useCase.processBatch();

        assertThat(count).isEqualTo(5);
        IntStream.range(0, attemptsList.size())
            .forEach(i -> verify(sender).send(subscription, eventsList.get(i), attemptsList.get(i)));
        verifyNoMoreInteractions(sender);
    }

    @Test
    void shouldPropagateFailureWhenOneAttemptThrowsDuringBatch() {
        Executor executor = Executors.newFixedThreadPool(3);
        ProcessDueDeliveries useCase = createUseCase(Clocks.fixed(), fixedRandom(0.5), executor);
        Subscription subscription = activeSubscription(SUBSCRIPTION_ID);
        DeliveryAttempt firstSuccessAttempt = dueAttempt(new EventId("evt-ok-a"), 1);
        DeliveryAttempt explodingAttempt = dueAttempt(new EventId("evt-boom"), 1);
        DeliveryAttempt secondSuccessAttempt = dueAttempt(new EventId("evt-ok-b"), 1);
        when(subscriptions.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(subscription));
        NotificationEvent firstSuccessEvent = stubSuccessForAttempt(firstSuccessAttempt, subscription);
        NotificationEvent explodingEvent = stubFailingAttempt(explodingAttempt, subscription);
        NotificationEvent secondSuccessEvent = stubSuccessForAttempt(secondSuccessAttempt, subscription);
        List<DeliveryAttempt> attemptsList = List.of(firstSuccessAttempt, explodingAttempt, secondSuccessAttempt);
        var claim = new DeliveryClaim(Clocks.NOW, 20, 5, Ids.WORKER_1, Duration.ofSeconds(16));
        when(attempts.claimDue(claim)).thenReturn(attemptsList);

        assertThatThrownBy(useCase::processBatch)
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(RuntimeException.class)
            .cause().hasMessage("webhook sender exploded");

        verify(sender).send(subscription, firstSuccessEvent, firstSuccessAttempt);
        verify(sender).send(subscription, explodingEvent, explodingAttempt);
        verify(sender).send(subscription, secondSuccessEvent, secondSuccessAttempt);
        verifyNoMoreInteractions(sender);
        DeliveryAttempt firstExecuted = firstSuccessAttempt.executed(Clocks.NOW, Ids.WORKER_1, DeliveryResult.of(DeliveryOutcomes.success()));
        DeliveryAttempt secondExecuted = secondSuccessAttempt.executed(Clocks.NOW, Ids.WORKER_1, DeliveryResult.of(DeliveryOutcomes.success()));
        verify(attempts).recordResultIf(firstExecuted, Ids.WORKER_1);
        verify(attempts).recordResultIf(secondExecuted, Ids.WORKER_1);
    }
}
