package co.cobre.notifications.application.port;

import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryOutcome;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.domain.Subscription;

public interface WebhookSender {

    DeliveryOutcome send(Subscription subscription, NotificationEvent event, DeliveryAttempt attempt);
}
