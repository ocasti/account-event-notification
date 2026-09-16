package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.domain.DeliveryStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.BindParam;

import java.time.Instant;
import java.util.Optional;

/**
 * Request DTO for listing notification events.
 */
public record ListRequest(
    @Parameter(
        description = "Only events created at or after this instant (ISO-8601)",
        example = "2024-03-15T00:00:00Z"
    )
    Optional<Instant> from,
    @Parameter(
        description = "Only events created at or before this instant (ISO-8601)",
        example = "2024-03-15T23:59:59Z"
    )
    Optional<Instant> to,
    @Parameter(
        name = "delivery_status",
        description = "Filters by delivery status: pending, retrying, completed or failed",
        example = "failed"
    )
    @BindParam("delivery_status")
    Optional<String> deliveryStatus,
    @Parameter(
        description = "Maximum number of events to return, between 1 and 100",
        example = "20"
    )
    @Min(1)
    @Max(100)
    Integer limit,
    @Parameter(
        description = "Opaque pagination cursor returned as next_cursor by a previous call",
        example = "cursor123"
    )
    Optional<String> cursor
) {
    public ListRequest {
        if (limit == null) {
            limit = 20;
        }
    }

    public Optional<DeliveryStatus> status() {
        return deliveryStatus.map(s -> {
            try {
                return DeliveryStatus.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid delivery status: " + s);
            }
        });
    }
}
