package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.application.usecase.NotificationEventDetail;
import co.cobre.notifications.application.usecase.NotificationEventSummaryPage;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.NotificationEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class NotificationEventResponseMapper {

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

    public NotificationEventPageResponse toPage(NotificationEventSummaryPage page) {
        return new NotificationEventPageResponse(
            page.items().stream()
                .map(summary -> toResponse(summary.event(), summary.attemptsCount()))
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
            attempt.latency().map(Duration::toMillis),
            attempt.origin().toString().toLowerCase()
        );
    }
}
