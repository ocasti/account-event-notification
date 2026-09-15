package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.application.usecase.GetNotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEvents;
import co.cobre.notifications.application.usecase.ReplayNotificationEvent;
import co.cobre.notifications.infrastructure.rest.dto.ListRequest;
import co.cobre.notifications.infrastructure.rest.dto.NotificationEventDetailResponse;
import co.cobre.notifications.infrastructure.rest.dto.NotificationEventPageResponse;
import co.cobre.notifications.infrastructure.rest.dto.ReplayResponse;
import co.cobre.notifications.infrastructure.rest.mapper.NotificationEventResponseMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;

import static org.springframework.http.HttpStatus.ACCEPTED;

/**
 * REST controller for notification events.
 */
@RestController
@RequestMapping("/notification_events")
public class NotificationEventController {
    private final ListNotificationEvents listNotificationEvents;
    private final GetNotificationEvent getNotificationEvent;
    private final ReplayNotificationEvent replayNotificationEvent;
    private final NotificationEventResponseMapper mapper;

    /**
     * Creates a new notification event controller.
     */
    public NotificationEventController(
        ListNotificationEvents listNotificationEvents,
        GetNotificationEvent getNotificationEvent,
        ReplayNotificationEvent replayNotificationEvent,
        NotificationEventResponseMapper mapper
    ) {
        this.listNotificationEvents = listNotificationEvents;
        this.getNotificationEvent = getNotificationEvent;
        this.replayNotificationEvent = replayNotificationEvent;
        this.mapper = mapper;
    }

    /**
     * Lists notification events.
     */
    @GetMapping
    public NotificationEventPageResponse list(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @ModelAttribute ListRequest request
    ) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Gets a notification event by ID.
     */
    @GetMapping("/{notification_event_id}")
    public NotificationEventDetailResponse get(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable("notification_event_id") String id
    ) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Replays a notification event.
     */
    @PostMapping("/{notification_event_id}/replay")
    @ResponseStatus(ACCEPTED)
    public ResponseEntity<ReplayResponse> replay(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable("notification_event_id") String id
    ) {
        throw new UnsupportedOperationException("not implemented");
    }
}
