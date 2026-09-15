package co.cobre.notifications.application.query;

import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.DeliveryStatus;

import java.time.Instant;
import java.util.Optional;

/**
 * Query to list notification events with optional filtering.
 */
public record ListNotificationEventsQuery(
    ClientId clientId,
    Optional<Instant> from,
    Optional<Instant> to,
    Optional<DeliveryStatus> status,
    int limit,
    Optional<String> cursor
) {}
