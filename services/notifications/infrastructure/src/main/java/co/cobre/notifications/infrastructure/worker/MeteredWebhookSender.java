package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.port.WebhookSender;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryOutcome;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.infrastructure.webhook.HttpWebhookSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


/**
 * Metered decorator for WebhookSender that records delivery latency metrics.
 * Delegates to HttpWebhookSender and records the latency of each delivery outcome.
 */
@Component
@org.springframework.context.annotation.Primary
public class MeteredWebhookSender implements WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(MeteredWebhookSender.class);

    private final HttpWebhookSender delegate;
    private final DeliveryMetrics metrics;

    public MeteredWebhookSender(HttpWebhookSender delegate, DeliveryMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public DeliveryOutcome send(Subscription subscription, NotificationEvent event, DeliveryAttempt attempt) {
        var outcome = delegate.send(subscription, event, attempt);

        var latency = switch (outcome) {
            case DeliveryOutcome.Success s -> s.latency();
            case DeliveryOutcome.TransientFailure tf -> tf.latency();
            case DeliveryOutcome.PermanentFailure pf -> pf.latency();
        };

        metrics.webhookLatency(event.clientId().value(), latency);
        logAttempt(outcome, event, attempt);

        return outcome;
    }

    private void logAttempt(DeliveryOutcome outcome, NotificationEvent event, DeliveryAttempt attempt) {
        var latency = switch (outcome) {
            case DeliveryOutcome.Success s -> s.latency();
            case DeliveryOutcome.TransientFailure tf -> tf.latency();
            case DeliveryOutcome.PermanentFailure pf -> pf.latency();
        };

        var outcomeType = switch (outcome) {
            case DeliveryOutcome.Success s -> "success";
            case DeliveryOutcome.TransientFailure tf -> "transient_failure";
            case DeliveryOutcome.PermanentFailure pf -> "permanent_failure";
        };

        var responseStatusValue = switch (outcome) {
            case DeliveryOutcome.Success s -> (Object) s.responseStatus();
            case DeliveryOutcome.TransientFailure tf -> (Object) tf.responseStatus().orElse(null);
            case DeliveryOutcome.PermanentFailure pf -> (Object) pf.responseStatus();
        };

        var responseStatus = responseStatusValue != null ? responseStatusValue : "none";

        var logBuilder = log.atInfo()
            .addKeyValue("event_id", event.eventId().value())
            .addKeyValue("client_id", event.clientId().value())
            .addKeyValue("cycle", attempt.cycle())
            .addKeyValue("attempt_number", attempt.attemptNumber())
            .addKeyValue("outcome", outcomeType)
            .addKeyValue("response_status", responseStatus)
            .addKeyValue("latency_ms", latency.toMillis());

        // Add reason for failures only
        switch (outcome) {
            case DeliveryOutcome.TransientFailure tf -> logBuilder.addKeyValue("reason", tf.reason());
            case DeliveryOutcome.PermanentFailure pf -> logBuilder.addKeyValue("reason", pf.reason());
            default -> {}
        }

        logBuilder.log("webhook delivery attempt");
    }
}
