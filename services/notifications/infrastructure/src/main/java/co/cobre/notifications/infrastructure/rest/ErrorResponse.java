package co.cobre.notifications.infrastructure.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for error responses.
 */
public record ErrorResponse(
    @Schema(description = "Machine-readable error code", example = "replay_not_allowed")
    @JsonProperty("code")
    String code,
    @Schema(description = "Human-readable error message", example = "Replay not allowed for this event")
    @JsonProperty("message")
    String message
) {}
