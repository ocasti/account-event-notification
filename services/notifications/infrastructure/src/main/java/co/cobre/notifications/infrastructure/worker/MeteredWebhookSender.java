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

import java.time.Duration;

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
        var fields = AttemptLogFields.from(outcome);

        metrics.webhookLatency(event.clientId().value(), fields.latency());
        logAttempt(fields, event, attempt);

        return outcome;
    }

    private void logAttempt(AttemptLogFields fields, NotificationEvent event, DeliveryAttempt attempt) {
        var responseStatus = fields.responseStatus() != null ? fields.responseStatus() : "none";

        var logBuilder = log.atInfo()
            .addKeyValue("event_id", event.eventId().value())
            .addKeyValue("client_id", event.clientId().value())
            .addKeyValue("cycle", attempt.cycle())
            .addKeyValue("attempt_number", attempt.attemptNumber())
            .addKeyValue("outcome", fields.outcomeType())
            .addKeyValue("response_status", responseStatus)
            .addKeyValue("latency_ms", fields.latency().toMillis());

        if (fields.reason() != null) {
            logBuilder.addKeyValue("reason", fields.reason());
        }

        logBuilder.log("webhook delivery attempt");
    }

    /**
     * Fields derived from a {@link DeliveryOutcome} needed for metrics and logging.
     * Built from a single switch so the outcome types are inspected only once per attempt.
     */
    private record AttemptLogFields(Duration latency, String outcomeType, Object responseStatus, String reason) {
        static AttemptLogFields from(DeliveryOutcome outcome) {
            return switch (outcome) {
                case DeliveryOutcome.Success s ->
                    new AttemptLogFields(s.latency(), "success", s.responseStatus(), null);
                case DeliveryOutcome.TransientFailure tf ->
                    new AttemptLogFields(tf.latency(), "transient_failure", tf.responseStatus().orElse(null), tf.reason());
                case DeliveryOutcome.PermanentFailure pf ->
                    new AttemptLogFields(pf.latency(), "permanent_failure", pf.responseStatus(), pf.reason());
            };
        }
    }
}
