package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.application.usecase.NotificationEventDetail;
import co.cobre.notifications.application.usecase.NotificationEventPage;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.infrastructure.rest.DeliveryAttemptResponse;
import co.cobre.notifications.infrastructure.rest.NotificationEventDetailResponse;
import co.cobre.notifications.infrastructure.rest.NotificationEventPageResponse;
import co.cobre.notifications.infrastructure.rest.NotificationEventResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Mapper for converting domain objects to REST response DTOs.
 */
@Component
public class NotificationEventResponseMapper {

    /**
     * Maps a notification event to a response DTO.
     */
    public NotificationEventResponse toResponse(NotificationEvent event, int attemptsCount) {
        return new NotificationEventResponse(
            event.eventId().value(),
            event.eventKey().value(),
            event.clientId().value(),
            event.content(),
            event.createdAt(),
            event.status().toString().toLowerCase(),
            event.deliveredAt(),
            attemptsCount
        );
    }

    /**
     * Maps a notification event detail to a response DTO.
     */
    public NotificationEventDetailResponse toDetail(NotificationEventDetail detail) {
        var event = detail.event();
        var attempts = detail.attempts();

        Optional<Instant> nextAttemptAt = attempts.stream()
            .filter(a -> !a.isExecuted())
            .findFirst()
            .map(DeliveryAttempt::nextAttemptAt);

        return new NotificationEventDetailResponse(
            event.eventId().value(),
            event.eventKey().value(),
            event.clientId().value(),
            event.content(),
            event.createdAt(),
            event.status().toString().toLowerCase(),
            event.deliveredAt(),
            attempts.size(),
            attempts.stream().map(this::mapAttempt).toList(),
            nextAttemptAt
        );
    }

    /**
     * Maps a notification event page to a response DTO.
     */
    public NotificationEventPageResponse toPage(NotificationEventPage page) {
        return new NotificationEventPageResponse(
            page.items().stream()
                .map(event -> toResponse(event, 0))
                .toList(),
            page.nextCursor()
        );
    }

    private DeliveryAttemptResponse mapAttempt(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
            attempt.cycle(),
            attempt.attemptNumber(),
            attempt.executedAt(),
            attempt.responseStatus(),
            attempt.failureReason(),
            attempt.latency().map(d -> d.toMillis()),
            attempt.origin().toString().toLowerCase()
        );
    }
}
