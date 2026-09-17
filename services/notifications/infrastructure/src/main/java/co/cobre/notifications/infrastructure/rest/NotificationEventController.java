package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.application.usecase.GetNotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEvents;
import co.cobre.notifications.application.usecase.ReplayNotificationEvent;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.infrastructure.security.AuthenticatedClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @Operation(summary = "List notification events")
    @GetMapping
    public NotificationEventPageResponse list(
        @AuthenticatedClient ClientId clientId,
        @Valid @ModelAttribute @ParameterObject ListRequest request
    ) {
        return mapper.toPage(listNotificationEvents.list(request.toQuery(clientId)));
    }

    @Operation(summary = "Get a notification event")
    @GetMapping("/{notification_event_id}")
    public NotificationEventDetailResponse get(
        @AuthenticatedClient ClientId clientId,
        @PathVariable("notification_event_id") String id
    ) {
        return mapper.toDetail(getNotificationEvent.get(clientId, new EventId(id)));
    }

    @Operation(summary = "Replay a notification event")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/{notification_event_id}/replay")
    public ReplayResponse replay(
        @AuthenticatedClient ClientId clientId,
        @PathVariable("notification_event_id") String id
    ) {
        return mapper.toReplay(replayNotificationEvent.replay(clientId, new EventId(id)));
    }
}
