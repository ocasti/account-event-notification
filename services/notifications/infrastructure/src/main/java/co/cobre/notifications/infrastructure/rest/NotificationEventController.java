package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.application.query.ListNotificationEventsQuery;
import co.cobre.notifications.application.usecase.GetNotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEvents;
import co.cobre.notifications.application.usecase.ReplayNotificationEvent;
import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.infrastructure.rest.dto.ListRequest;
import co.cobre.notifications.infrastructure.rest.dto.NotificationEventDetailResponse;
import co.cobre.notifications.infrastructure.rest.dto.NotificationEventPageResponse;
import co.cobre.notifications.infrastructure.rest.dto.ReplayResponse;
import co.cobre.notifications.infrastructure.rest.mapper.NotificationEventResponseMapper;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.Optional;

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

        Optional<DeliveryStatus> status = Optional.empty();
        if (request.deliveryStatus().isPresent()) {
            try {
                status = Optional.of(DeliveryStatus.valueOf(request.deliveryStatus().get().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid delivery status: " + request.deliveryStatus().get());
            }
        }

        var query = new ListNotificationEventsQuery(
            clientId,
            request.from(),
            request.to(),
            status,
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
    @ResponseStatus(ACCEPTED)
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

        var uri = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .replacePath("/notification_events/{id}")
            .buildAndExpand(id)
            .toUri();

        return ResponseEntity.accepted()
            .location(uri)
            .body(response);
    }
}
