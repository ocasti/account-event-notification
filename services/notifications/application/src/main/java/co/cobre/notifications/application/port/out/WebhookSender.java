package co.cobre.notifications.application.port.out;

import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.DeliveryOutcome;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.domain.model.Subscription;

/**
 * Port for sending webhook notifications.
 */
public interface WebhookSender {

    /**
     * Sends a notification via webhook.
     */
    DeliveryOutcome send(Subscription subscription, NotificationEvent event, DeliveryAttempt attempt);
}
