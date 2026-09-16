package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.application.usecase.ListNotificationEventsQuery;
import co.cobre.notifications.application.usecase.GetNotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEvents;
import co.cobre.notifications.application.usecase.ReplayNotificationEvent;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.infrastructure.security.AuthenticatedClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification_events")
@Tag(name = "Notification events")
public class NotificationEventController {
    private final ListNotificationEvents listNotificationEvents;
    private final GetNotificationEvent getNotificationEvent;
    private final ReplayNotificationEvent replayNotificationEvent;
    private final NotificationEventResponseMapper mapper;

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

    @Operation(
        summary = "List notification events",
        description = "Lists the notification events belonging to the authenticated client, "
            + "newest first, optionally filtered by delivery status and creation date range."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of notification events"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid query parameter (delivery_status, from/to, limit or cursor)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid bearer token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping
    public NotificationEventPageResponse list(
        @AuthenticatedClient ClientId clientId,
        @Valid @ModelAttribute @ParameterObject ListRequest request
    ) {
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

    @Operation(
        summary = "Get a notification event",
        description = "Gets a single notification event owned by the authenticated client, "
            + "with its full delivery attempt history."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notification event detail"),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid bearer token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No event with this ID for the authenticated client",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping("/{notification_event_id}")
    public NotificationEventDetailResponse get(
        @AuthenticatedClient ClientId clientId,
        @PathVariable("notification_event_id") String id
    ) {
        var eventId = new EventId(id);

        var detail = getNotificationEvent.get(clientId, eventId);
        return mapper.toDetail(detail);
    }

    @Operation(
        summary = "Replay a notification event",
        description = "Schedules a new delivery cycle for a failed notification event owned "
            + "by the authenticated client."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Replay accepted and scheduled"),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid bearer token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No event with this ID for the authenticated client",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "The event is not in a state that allows a replay",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PostMapping("/{notification_event_id}/replay")
    public ResponseEntity<ReplayResponse> replay(
        @AuthenticatedClient ClientId clientId,
        @PathVariable("notification_event_id") String id
    ) {
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
