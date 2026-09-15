package co.cobre.notifications.application.query;

import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.NotificationEvent;

import java.util.List;

/**
 * Detail view of a notification event with its delivery attempts.
 */
public record NotificationEventDetail(
    NotificationEvent event,
    List<DeliveryAttempt> attempts
) {}
