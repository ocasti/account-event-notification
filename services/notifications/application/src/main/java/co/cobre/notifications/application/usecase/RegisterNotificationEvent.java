package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.command.RegisterEventCommand;
import co.cobre.notifications.application.command.RegistrationResult;
import co.cobre.notifications.application.port.out.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.port.out.SubscriptionRepository;

import java.time.Clock;

/**
 * Use case for registering a new notification event.
 */
public final class RegisterNotificationEvent {
    private final NotificationEventRepository events;
    private final DeliveryAttemptRepository attempts;
    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    /**
     * Creates a new register notification event use case.
     */
    public RegisterNotificationEvent(
        NotificationEventRepository events,
        DeliveryAttemptRepository attempts,
        SubscriptionRepository subscriptions,
        Clock clock
    ) {
        this.events = events;
        this.attempts = attempts;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    /**
     * Registers a notification event.
     */
    public RegistrationResult register(RegisterEventCommand command) {
        throw new UnsupportedOperationException("not implemented");
    }
}
