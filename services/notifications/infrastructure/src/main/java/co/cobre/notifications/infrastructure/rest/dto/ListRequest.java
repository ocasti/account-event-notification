package co.cobre.notifications.infrastructure.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.Optional;

/**
 * Request DTO for listing notification events.
 */
public record ListRequest(
    Optional<Instant> from,
    Optional<Instant> to,
    Optional<String> deliveryStatus,
    @Min(1)
    @Max(100)
    Integer limit,
    Optional<String> cursor
) {
    public ListRequest {
        if (limit == null) {
            limit = 20;
        }
    }
}
