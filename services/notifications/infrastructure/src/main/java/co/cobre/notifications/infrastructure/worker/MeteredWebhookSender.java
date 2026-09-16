package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.port.WebhookSender;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryOutcome;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.infrastructure.webhook.HttpWebhookSender;
import org.springframework.stereotype.Component;

/**
 * Metered decorator for WebhookSender that records delivery latency metrics.
 */
@Component
@org.springframework.context.annotation.Primary
public class MeteredWebhookSender implements WebhookSender {

    private final HttpWebhookSender delegate;
    private final DeliveryMetrics metrics;

    public MeteredWebhookSender(HttpWebhookSender delegate, DeliveryMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public DeliveryOutcome send(Subscription subscription, NotificationEvent event, DeliveryAttempt attempt) {
        throw new UnsupportedOperationException();
    }
}
