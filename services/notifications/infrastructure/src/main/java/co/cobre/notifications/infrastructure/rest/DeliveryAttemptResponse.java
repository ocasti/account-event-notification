package co.cobre.notifications.infrastructure.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Optional;

public record DeliveryAttemptResponse(
    @Schema(description = "Delivery cycle this attempt belongs to; a replay starts a new cycle", example = "1")
    @JsonProperty("cycle")
    int cycle,
    @Schema(description = "Attempt number within the cycle", example = "1")
    @JsonProperty("attempt_number")
    int attemptNumber,
    @Schema(description = "When the attempt was executed, if it already ran")
    @JsonProperty("executed_at")
    Optional<Instant> executedAt,
    @Schema(description = "HTTP status returned by the client webhook, if the attempt ran", example = "503")
    @JsonProperty("response_status")
    Optional<Integer> responseStatus,
    @Schema(description = "Reason the attempt failed, if it did", example = "timeout")
    @JsonProperty("failure_reason")
    Optional<String> failureReason,
    @Schema(description = "Round-trip latency in milliseconds, if the attempt ran", example = "184")
    @JsonProperty("latency_ms")
    Optional<Long> latencyMs,
    @Schema(description = "Origin of the attempt: system or replay", example = "system")
    @JsonProperty("origin")
    String origin
) {}
