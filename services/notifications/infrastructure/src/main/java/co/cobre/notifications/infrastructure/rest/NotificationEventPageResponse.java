package co.cobre.notifications.infrastructure.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Optional;

/**
 * Response DTO for a paginated list of notification events.
 */
public record NotificationEventPageResponse(
    @Schema(description = "Page of notification events, newest first")
    @JsonProperty("items")
    List<NotificationEventResponse> items,
    @Schema(description = "Cursor to fetch the next page, absent when there are no more results", example = "cursor123")
    @JsonProperty("next_cursor")
    Optional<String> nextCursor
) {}
