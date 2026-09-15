package co.cobre.notifications.application.port;

import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryOutcome;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.Subscription;

/**
 * Port for sending webhook notifications.
 */
public interface WebhookSender {

    /**
     * Sends a notification via webhook.
     */
    DeliveryOutcome send(Subscription subscription, NotificationEvent event, DeliveryAttempt attempt);
}
