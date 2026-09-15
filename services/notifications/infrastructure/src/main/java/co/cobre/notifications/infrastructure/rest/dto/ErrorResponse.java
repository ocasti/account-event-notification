package co.cobre.notifications.infrastructure.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for error responses.
 */
public record ErrorResponse(
    @JsonProperty("code")
    String code,
    @JsonProperty("message")
    String message
) {}
