package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.application.usecase.ListNotificationEventsQuery;
import co.cobre.notifications.application.usecase.GetNotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEvents;
import co.cobre.notifications.application.usecase.ReplayNotificationEvent;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.infrastructure.rest.ListRequest;
import co.cobre.notifications.infrastructure.rest.NotificationEventDetailResponse;
import co.cobre.notifications.infrastructure.rest.NotificationEventPageResponse;
import co.cobre.notifications.infrastructure.rest.ReplayResponse;
import co.cobre.notifications.infrastructure.rest.NotificationEventResponseMapper;
import co.cobre.notifications.infrastructure.security.ClientIdResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

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
    private final ClientIdResolver clientIdResolver;

    /**
     * Creates a new notification event controller.
     */
    public NotificationEventController(
        ListNotificationEvents listNotificationEvents,
        GetNotificationEvent getNotificationEvent,
        ReplayNotificationEvent replayNotificationEvent,
        NotificationEventResponseMapper mapper,
        ClientIdResolver clientIdResolver
    ) {
        this.listNotificationEvents = listNotificationEvents;
        this.getNotificationEvent = getNotificationEvent;
        this.replayNotificationEvent = replayNotificationEvent;
        this.mapper = mapper;
        this.clientIdResolver = clientIdResolver;
    }

    /**
     * Lists notification events.
     */
    @GetMapping
    public NotificationEventPageResponse list(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @ModelAttribute ListRequest request
    ) {
        ClientId clientId = clientIdResolver.resolve(jwt);

        var query = new ListNotificationEventsQuery(
            clientId,
            request.from(),
            request.to(),
            request.status(),
            request.limit(),
            request.cursor()
        );

        var page = listNotificationEvents.list(query);
        return mapper.toPage(page);
    }

    /**
     * Gets a notification event by ID.
     */
    @GetMapping("/{notification_event_id}")
    public NotificationEventDetailResponse get(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable("notification_event_id") String id
    ) {
        ClientId clientId = clientIdResolver.resolve(jwt);
        var eventId = new EventId(id);

        var detail = getNotificationEvent.get(clientId, eventId);
        return mapper.toDetail(detail);
    }

    /**
     * Replays a notification event.
     */
    @PostMapping("/{notification_event_id}/replay")
    public ResponseEntity<ReplayResponse> replay(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable("notification_event_id") String id
    ) {
        ClientId clientId = clientIdResolver.resolve(jwt);
        var eventId = new EventId(id);

        var result = replayNotificationEvent.replay(clientId, eventId);

        var response = new ReplayResponse(
            result.eventId().value(),
            result.cycle(),
            "pending"
        );

        return ResponseEntity.accepted().body(response);
    }
}
