package co.cobre.notifications.application.usecase;

import co.cobre.notifications.application.port.out.DeliveryAttemptRepository;
import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.query.ReplayResult;
import co.cobre.notifications.domain.exception.NotificationEventNotFoundException;
import co.cobre.notifications.domain.exception.ReplayNotAllowedException;
import co.cobre.notifications.domain.model.AttemptOrigin;
import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.EventId;

import java.time.Clock;

/**
 * Use case for replaying a failed notification event.
 */
public final class ReplayNotificationEvent {
    private final NotificationEventRepository events;
    private final DeliveryAttemptRepository attempts;
    private final Clock clock;

    /**
     * Creates a new replay notification event use case.
     */
    public ReplayNotificationEvent(
        NotificationEventRepository events,
        DeliveryAttemptRepository attempts,
        Clock clock
    ) {
        this.events = events;
        this.attempts = attempts;
        this.clock = clock;
    }

    /**
     * Replays a notification event.
     */
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
