package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.UseCase;
import co.cobre.notifications.application.usecase.RegisterEventCommand;
import co.cobre.notifications.application.usecase.RegistrationResult;
import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.port.SubscriptionRepository;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventData;
import co.cobre.notifications.domain.NotificationEvent;

import java.time.Clock;

/**
 * Use case for registering a new notification event.
 */
@UseCase
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
        if (events.existsById(command.eventId())) {
            return RegistrationResult.DUPLICATE;
        }

        var data = new EventData(
            command.eventId(),
            command.clientId(),
            command.eventKey(),
            command.content(),
            command.occurredAt()
        );

        var subscription = subscriptions.findActive(command.clientId(), command.eventKey());

        if (subscription.isEmpty()) {
            var skippedEvent = NotificationEvent.skipped(data, clock.instant());
            events.save(skippedEvent);
            return RegistrationResult.SKIPPED;
        }

        var now = clock.instant();
        var registeredEvent = NotificationEvent.register(data, now, subscription.get());
        events.save(registeredEvent);

        var attempt = DeliveryAttempt.first(command.eventId(), 0, now, AttemptOrigin.SYSTEM);
        attempts.save(attempt);

        return RegistrationResult.REGISTERED;
    }
}
