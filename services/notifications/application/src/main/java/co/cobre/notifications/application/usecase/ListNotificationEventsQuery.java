package co.cobre.notifications.application.usecase;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;

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
