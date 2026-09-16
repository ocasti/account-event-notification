package co.cobre.notifications.application.usecase;

import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.NotificationEvent;

import java.util.List;

public record NotificationEventDetail(
    NotificationEvent event,
    List<DeliveryAttempt> attempts
) {}
