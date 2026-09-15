package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.infrastructure.persistence.DeliveryStatusEntity;

import java.time.Instant;
import java.util.Optional;

public record SearchCriteria(
    String clientId,
    Optional<DeliveryStatusEntity> status,
    Optional<Instant> from,
    Optional<Instant> to,
    Optional<Instant> cursorCreatedAt,
    Optional<String> cursorEventId,
    int limit
) {}
