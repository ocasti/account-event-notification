package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.out.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.port.out.SubscriptionRepository;
import co.cobre.notifications.application.port.out.WebhookSender;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.DeliveryOutcome;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.NotificationEvent;
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
        var dueDue = attempts.claimDue(clock.instant(), batchSize, maxPerClient, workerId, lease);
        for (var attempt : dueDue) {
            process(attempt);
        }
        return dueDue.size();
    }

    /**
     * Processes a single delivery attempt.
     */
    public void process(DeliveryAttempt attempt) {
        var event = events.findById(attempt.eventId());
        if (event.isEmpty()) {
            return;
        }

        var notificationEvent = event.get();
        var subscription = subscriptions.findById(notificationEvent.subscriptionId().orElse(null));

        var now = clock.instant();
        DeliveryOutcome outcome;
        String failureReason = null;

        if (subscription.isEmpty() || !subscription.get().active()) {
            outcome = null;
            failureReason = "subscription unavailable";
        } else {
            outcome = sender.send(subscription.get(), notificationEvent, attempt);
        }

        DeliveryAttempt executed;
        if (outcome != null) {
            executed = new DeliveryAttempt(
                attempt.id(),
                attempt.eventId(),
                attempt.cycle(),
                attempt.attemptNumber(),
                attempt.nextAttemptAt(),
                attempt.claimedAt(),
                attempt.claimedBy(),
                java.util.Optional.of(now),
                extractResponseStatus(outcome),
                extractFailureReason(outcome),
                extractLatency(outcome),
                attempt.origin()
            );
        } else {
            executed = new DeliveryAttempt(
                attempt.id(),
                attempt.eventId(),
                attempt.cycle(),
                attempt.attemptNumber(),
                attempt.nextAttemptAt(),
                attempt.claimedAt(),
                attempt.claimedBy(),
                java.util.Optional.of(now),
                java.util.Optional.empty(),
                java.util.Optional.of(failureReason),
                java.util.Optional.empty(),
                attempt.origin()
            );
        }

        executed = new DeliveryAttempt(
            executed.id(),
            executed.eventId(),
            executed.cycle(),
            executed.attemptNumber(),
            executed.nextAttemptAt(),
            executed.claimedAt(),
            java.util.Optional.of(workerId),
            executed.executedAt(),
            executed.responseStatus(),
            executed.failureReason(),
            executed.latency(),
            executed.origin()
        );

        boolean recorded = attempts.recordResultIf(executed, workerId);
        if (!recorded) {
            return;
        }

        var previousStatus = notificationEvent.status();

        if (outcome instanceof DeliveryOutcome.Success) {
            notificationEvent.complete(now);
            events.transition(attempt.eventId(), previousStatus, notificationEvent);
        } else if (outcome instanceof DeliveryOutcome.TransientFailure) {
            if (retryPolicy.isExhausted(attempt.attemptNumber() + 1)) {
                notificationEvent.fail();
            } else {
                var delay = retryPolicy.delayBefore(attempt.attemptNumber() + 1, random);
                var nextAttempt = attempt.next(now.plus(delay));
                attempts.save(nextAttempt);
                notificationEvent.scheduleRetry();
            }
            events.transition(attempt.eventId(), previousStatus, notificationEvent);
        } else if (outcome instanceof DeliveryOutcome.PermanentFailure) {
            notificationEvent.fail();
            events.transition(attempt.eventId(), previousStatus, notificationEvent);
        } else {
            notificationEvent.fail();
            events.transition(attempt.eventId(), previousStatus, notificationEvent);
        }
    }

    private java.util.Optional<Integer> extractResponseStatus(DeliveryOutcome outcome) {
        if (outcome instanceof DeliveryOutcome.Success success) {
            return java.util.Optional.of(success.responseStatus());
        } else if (outcome instanceof DeliveryOutcome.TransientFailure tf) {
            return tf.responseStatus();
        } else if (outcome instanceof DeliveryOutcome.PermanentFailure permanent) {
            return java.util.Optional.of(permanent.responseStatus());
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<String> extractFailureReason(DeliveryOutcome outcome) {
        if (outcome instanceof DeliveryOutcome.TransientFailure tf) {
            return java.util.Optional.of(tf.reason());
        } else if (outcome instanceof DeliveryOutcome.PermanentFailure permanent) {
            return java.util.Optional.of(permanent.reason());
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Duration> extractLatency(DeliveryOutcome outcome) {
        if (outcome instanceof DeliveryOutcome.Success success) {
            return java.util.Optional.of(success.latency());
        } else if (outcome instanceof DeliveryOutcome.TransientFailure tf) {
            return java.util.Optional.of(tf.latency());
        } else if (outcome instanceof DeliveryOutcome.PermanentFailure permanent) {
            return java.util.Optional.of(permanent.latency());
        }
        return java.util.Optional.empty();
    }
}
