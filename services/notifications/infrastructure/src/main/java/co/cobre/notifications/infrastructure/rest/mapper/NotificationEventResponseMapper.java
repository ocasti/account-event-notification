package co.cobre.notifications.infrastructure.rest.mapper;

import co.cobre.notifications.application.query.NotificationEventDetail;
import co.cobre.notifications.application.query.NotificationEventPage;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.infrastructure.rest.dto.DeliveryAttemptResponse;
import co.cobre.notifications.infrastructure.rest.dto.NotificationEventDetailResponse;
import co.cobre.notifications.infrastructure.rest.dto.NotificationEventPageResponse;
import co.cobre.notifications.infrastructure.rest.dto.NotificationEventResponse;
import org.springframework.stereotype.Component;

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
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Maps a notification event detail to a response DTO.
     */
    public NotificationEventDetailResponse toDetail(NotificationEventDetail detail) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Maps a notification event page to a response DTO.
     */
    public NotificationEventPageResponse toPage(NotificationEventPage page) {
        throw new UnsupportedOperationException("not implemented");
    }

    private DeliveryAttemptResponse mapAttempt(DeliveryAttempt attempt) {
        throw new UnsupportedOperationException("not implemented");
    }
}
