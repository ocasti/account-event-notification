package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.out.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.port.out.SubscriptionRepository;
import co.cobre.notifications.application.port.out.WebhookSender;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.policy.RetryPolicy;

import java.time.Clock;
import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * Use case for processing delivery attempts that are due.
 */
public final class ProcessDueDeliveries {
    private final NotificationEventRepository events;
    private final DeliveryAttemptRepository attempts;
    private final SubscriptionRepository subscriptions;
    private final WebhookSender sender;
    private final RetryPolicy retryPolicy;
    private final RandomGenerator random;
    private final Clock clock;
    private final String workerId;
    private final int batchSize;
    private final int maxPerClient;
    private final Duration lease;

    /**
     * Creates a new process due deliveries use case.
     */
    public ProcessDueDeliveries(
        NotificationEventRepository events,
        DeliveryAttemptRepository attempts,
        SubscriptionRepository subscriptions,
        WebhookSender sender,
        RetryPolicy retryPolicy,
        RandomGenerator random,
        Clock clock,
        String workerId,
        int batchSize,
        int maxPerClient,
        Duration lease
    ) {
        this.events = events;
        this.attempts = attempts;
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.retryPolicy = retryPolicy;
        this.random = random;
        this.clock = clock;
        this.workerId = workerId;
        this.batchSize = batchSize;
        this.maxPerClient = maxPerClient;
        this.lease = lease;
    }

    /**
     * Processes a batch of due delivery attempts.
     */
    public int processBatch() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Processes a single delivery attempt.
     */
    public void process(DeliveryAttempt attempt) {
        throw new UnsupportedOperationException("not implemented");
    }
}
