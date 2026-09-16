package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.UseCase;
import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.ReplayNotAllowedException;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;

import java.time.Clock;

@UseCase
public final class ReplayNotificationEvent {
    private final NotificationEventRepository events;
    private final DeliveryAttemptRepository attempts;
    private final Clock clock;

    public ReplayNotificationEvent(
        NotificationEventRepository events,
        DeliveryAttemptRepository attempts,
        Clock clock
    ) {
        this.events = events;
        this.attempts = attempts;
        this.clock = clock;
    }

    public ReplayResult replay(ClientId clientId, EventId eventId) {
        var event = events.findByClientAndId(clientId, eventId)
            .orElseThrow(() -> new NotificationEventNotFoundException(eventId));

        if (event.status() != DeliveryStatus.FAILED) {
            throw new ReplayNotAllowedException(eventId, event.status());
        }

        event.replay();

        boolean transitioned = events.transition(eventId, DeliveryStatus.FAILED, event);
        if (!transitioned) {
            throw new ReplayNotAllowedException(eventId, event.status());
        }

        var attempt = DeliveryAttempt.first(eventId, event.cycle(), clock.instant(), AttemptOrigin.REPLAY);
        attempts.save(attempt);

        return new ReplayResult(eventId, event.cycle());
    }
}
