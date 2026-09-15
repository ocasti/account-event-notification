package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.out.DeliveryClaim;
import co.cobre.notifications.application.port.out.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.port.out.SubscriptionRepository;
import co.cobre.notifications.application.port.out.WebhookSender;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.DeliveryOutcome;
import co.cobre.notifications.domain.model.DeliveryResult;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.domain.model.Subscription;
import co.cobre.notifications.domain.policy.RetryPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
    private final DeliveryWorkerSettings settings;

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
        DeliveryWorkerSettings settings
    ) {
        this.events = events;
        this.attempts = attempts;
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.retryPolicy = retryPolicy;
        this.random = random;
        this.clock = clock;
        this.settings = settings;
    }

    /**
     * Processes a batch of due delivery attempts.
     */
    public int processBatch() {
        var claim = new DeliveryClaim(clock.instant(), settings.batchSize(), settings.maxPerClient(), settings.workerId(), settings.lease());
        var claimed = attempts.claimDue(claim);
        for (var attempt : claimed) {
            process(attempt);
        }
        return claimed.size();
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
        var now = clock.instant();

        var subscription = resolveSubscription(notificationEvent);
        var outcome = subscription
            .map(sub -> sender.send(sub, notificationEvent, attempt))
            .orElseGet(() -> new DeliveryOutcome.PermanentFailure(0, "subscription unavailable", java.time.Duration.ZERO));

        var result = DeliveryResult.of(outcome);
        var executed = attempt.executed(now, settings.workerId(), result);

        boolean recorded = attempts.recordResultIf(executed, settings.workerId());
        if (!recorded) {
            return;
        }

        var previousStatus = notificationEvent.status();
        handleOutcome(notificationEvent, attempt, outcome, now);
        events.transition(attempt.eventId(), previousStatus, notificationEvent);
    }

    private Optional<Subscription> resolveSubscription(NotificationEvent event) {
        return event.subscriptionId()
            .flatMap(subscriptions::findById)
            .filter(Subscription::active);
    }

    private void handleOutcome(NotificationEvent event, DeliveryAttempt attempt, DeliveryOutcome outcome, java.time.Instant now) {
        switch (outcome) {
            case DeliveryOutcome.Success s -> event.complete(now);
            case DeliveryOutcome.TransientFailure tf -> {
                if (retryPolicy.isExhausted(attempt.attemptNumber() + 1)) {
                    event.fail();
                } else {
                    var delay = retryPolicy.delayBefore(attempt.attemptNumber() + 1, random);
                    var nextAttempt = attempt.next(now.plus(delay));
                    attempts.save(nextAttempt);
                    event.scheduleRetry();
                }
            }
            case DeliveryOutcome.PermanentFailure pf -> event.fail();
        }
    }
}
