package co.cobre.notifications.infrastructure.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Response DTO for a paginated list of notification events.
 */
public record NotificationEventPageResponse(
    @JsonProperty("items")
    List<NotificationEventResponse> items,
    @JsonProperty("next_cursor")
    Optional<String> nextCursor
) {}
