package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.out.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.port.out.SubscriptionRepository;
import co.cobre.notifications.application.port.out.WebhookSender;
import co.cobre.notifications.domain.model.AttemptOrigin;
import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.DeliveryOutcome;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.domain.model.EventKey;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.domain.model.Subscription;
import co.cobre.notifications.domain.model.WebhookUrl;
import co.cobre.notifications.domain.policy.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(random.nextDouble()).thenReturn(value);
        return random;
    }

    private ProcessDueDeliveries createUseCase(Clock clock, RandomGenerator random) {
        return new ProcessDueDeliveries(
            events, attempts, subscriptions, sender,
            RetryPolicy.standard(),
            random,
            clock,
            "worker-1",
            20,
            5,
            Duration.ofSeconds(16)
        );
    }

    @Test
    void shouldProcessBatchByClaimingAndProcessing() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);

        ProcessDueDeliveries useCase = createUseCase(clock, random);

        EventId eventId = new EventId("evt-123");
        ClientId clientId = new ClientId("client-1");
        DeliveryAttempt attempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(),
            eventId,
            0,
            1,
            now.minusSeconds(60),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            AttemptOrigin.SYSTEM
        );

        when(attempts.claimDue(now, 20, 5, "worker-1", Duration.ofSeconds(16)))
            .thenReturn(List.of(attempt));

        int count = useCase.processBatch();

        assertThat(count).isEqualTo(1);
        verify(attempts).claimDue(now, 20, 5, "worker-1", Duration.ofSeconds(16));
    }

    @Test
    void shouldSuccessfullyCompleteDelivery() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);

        ProcessDueDeliveries useCase = createUseCase(clock, random);

        EventId eventId = new EventId("evt-success");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";

        DeliveryAttempt attempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(),
            eventId,
            0,
            1,
            now.minusSeconds(60),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            AttemptOrigin.SYSTEM
        );

        NotificationEvent event = new NotificationEvent(
            eventId,
            clientId,
            new EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z"),
            DeliveryStatus.PENDING,
            Optional.of(subscriptionId),
            0,
            Optional.empty()
        );

        Subscription subscription = new Subscription(
            subscriptionId,
            clientId,
            Set.of(new EventKey("order.created")),
            WebhookUrl.of("https://example.com/webhook"),
            Optional.empty(),
            Optional.empty(),
            true,
            Instant.parse("2025-01-01T10:00:00Z")
        );

        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, attempt))
            .thenReturn(new DeliveryOutcome.Success(200, Duration.ofMillis(120)));
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1")))
            .thenReturn(true);
        when(events.transition(eq(eventId), eq(DeliveryStatus.PENDING), any(NotificationEvent.class)))
            .thenReturn(true);

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
    void shouldRetryOnTransientFailureWithBudget() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);

        ProcessDueDeliveries useCase = createUseCase(clock, random);

        EventId eventId = new EventId("evt-retry");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";

        DeliveryAttempt attempt2 = new DeliveryAttempt(
            java.util.UUID.randomUUID(),
            eventId,
            0,
            2,
            now.minusSeconds(60),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            AttemptOrigin.SYSTEM
        );

        NotificationEvent event = new NotificationEvent(
            eventId,
            clientId,
            new EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z"),
            DeliveryStatus.RETRYING,
            Optional.of(subscriptionId),
            0,
            Optional.empty()
        );

        Subscription subscription = new Subscription(
            subscriptionId,
            clientId,
            Set.of(new EventKey("order.created")),
            WebhookUrl.of("https://example.com/webhook"),
            Optional.empty(),
            Optional.empty(),
            true,
            Instant.parse("2025-01-01T10:00:00Z")
        );

        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, attempt2))
            .thenReturn(new DeliveryOutcome.TransientFailure(Optional.of(503), "Service unavailable", Duration.ofMillis(50)));
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1")))
            .thenReturn(true);
        when(events.transition(eq(eventId), eq(DeliveryStatus.RETRYING), any(NotificationEvent.class)))
            .thenReturn(true);

        useCase.process(attempt2);

        verify(attempts).save(any(DeliveryAttempt.class));
        ArgumentCaptor<NotificationEvent> retryingCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).transition(eq(eventId), eq(DeliveryStatus.RETRYING), retryingCaptor.capture());

        NotificationEvent updated = retryingCaptor.getValue();
        assertThat(updated.status()).isEqualTo(DeliveryStatus.RETRYING);
    }

    @Test
    void shouldFailAfterExhaustedAttempts() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);

        ProcessDueDeliveries useCase = createUseCase(clock, random);

        EventId eventId = new EventId("evt-exhausted");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";

        DeliveryAttempt attempt5 = new DeliveryAttempt(
            java.util.UUID.randomUUID(),
            eventId,
            0,
            5,
            now.minusSeconds(60),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            AttemptOrigin.SYSTEM
        );

        NotificationEvent event = new NotificationEvent(
            eventId,
            clientId,
            new EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z"),
            DeliveryStatus.RETRYING,
            Optional.of(subscriptionId),
            0,
            Optional.empty()
        );

        Subscription subscription = new Subscription(
            subscriptionId,
            clientId,
            Set.of(new EventKey("order.created")),
            WebhookUrl.of("https://example.com/webhook"),
            Optional.empty(),
            Optional.empty(),
            true,
            Instant.parse("2025-01-01T10:00:00Z")
        );

        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, attempt5))
            .thenReturn(new DeliveryOutcome.TransientFailure(Optional.of(503), "Service unavailable", Duration.ofMillis(50)));
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1")))
            .thenReturn(true);
        when(events.transition(eq(eventId), eq(DeliveryStatus.RETRYING), any(NotificationEvent.class)))
            .thenReturn(true);

        useCase.process(attempt5);

        verify(attempts, never()).save(any(DeliveryAttempt.class));
        ArgumentCaptor<NotificationEvent> failedCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).transition(eq(eventId), eq(DeliveryStatus.RETRYING), failedCaptor.capture());

        NotificationEvent updated = failedCaptor.getValue();
        assertThat(updated.status()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void shouldFailOnPermanentFailure() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);

        ProcessDueDeliveries useCase = createUseCase(clock, random);

        EventId eventId = new EventId("evt-permanent");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";

        DeliveryAttempt attempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(),
            eventId,
            0,
            1,
            now.minusSeconds(60),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            AttemptOrigin.SYSTEM
        );

        NotificationEvent event = new NotificationEvent(
            eventId,
            clientId,
            new EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z"),
            DeliveryStatus.PENDING,
            Optional.of(subscriptionId),
            0,
            Optional.empty()
        );

        Subscription subscription = new Subscription(
            subscriptionId,
            clientId,
            Set.of(new EventKey("order.created")),
            WebhookUrl.of("https://example.com/webhook"),
            Optional.empty(),
            Optional.empty(),
            true,
            Instant.parse("2025-01-01T10:00:00Z")
        );

        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, attempt))
            .thenReturn(new DeliveryOutcome.PermanentFailure(404, "Not found", Duration.ofMillis(50)));
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1")))
            .thenReturn(true);
        when(events.transition(eq(eventId), eq(DeliveryStatus.PENDING), any(NotificationEvent.class)))
            .thenReturn(true);

        useCase.process(attempt);

        verify(attempts, never()).save(any(DeliveryAttempt.class));
        ArgumentCaptor<NotificationEvent> failedCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).transition(eq(eventId), eq(DeliveryStatus.PENDING), failedCaptor.capture());

        NotificationEvent updated = failedCaptor.getValue();
        assertThat(updated.status()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void shouldNotTransitionIfRecordResultFails() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);

        ProcessDueDeliveries useCase = createUseCase(clock, random);

        EventId eventId = new EventId("evt-record-fail");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-1";

        DeliveryAttempt attempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(),
            eventId,
            0,
            1,
            now.minusSeconds(60),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            AttemptOrigin.SYSTEM
        );

        NotificationEvent event = new NotificationEvent(
            eventId,
            clientId,
            new EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z"),
            DeliveryStatus.PENDING,
            Optional.of(subscriptionId),
            0,
            Optional.empty()
        );

        Subscription subscription = new Subscription(
            subscriptionId,
            clientId,
            Set.of(new EventKey("order.created")),
            WebhookUrl.of("https://example.com/webhook"),
            Optional.empty(),
            Optional.empty(),
            true,
            Instant.parse("2025-01-01T10:00:00Z")
        );

        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(sender.send(subscription, event, attempt))
            .thenReturn(new DeliveryOutcome.Success(200, Duration.ofMillis(120)));
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1")))
            .thenReturn(false);

        useCase.process(attempt);

        verify(events, never()).transition(any(), any(), any());
    }

    @Test
    void shouldFailWhenSubscriptionUnavailable() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        RandomGenerator random = fixedRandom(0.5);

        ProcessDueDeliveries useCase = createUseCase(clock, random);

        EventId eventId = new EventId("evt-no-sub");
        ClientId clientId = new ClientId("client-1");
        String subscriptionId = "sub-missing";

        DeliveryAttempt attempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(),
            eventId,
            0,
            1,
            now.minusSeconds(60),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            AttemptOrigin.SYSTEM
        );

        NotificationEvent event = new NotificationEvent(
            eventId,
            clientId,
            new EventKey("order.created"),
            "Order created",
            Instant.parse("2025-01-01T11:00:00Z"),
            Instant.parse("2025-01-01T11:00:01Z"),
            DeliveryStatus.PENDING,
            Optional.of(subscriptionId),
            0,
            Optional.empty()
        );

        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.empty());
        when(attempts.recordResultIf(any(DeliveryAttempt.class), eq("worker-1")))
            .thenReturn(true);
        when(events.transition(eq(eventId), eq(DeliveryStatus.PENDING), any(NotificationEvent.class)))
            .thenReturn(true);

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
}
