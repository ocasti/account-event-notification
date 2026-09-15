package co.cobre.notifications.infrastructure.persistence.jpa;

import co.cobre.notifications.infrastructure.persistence.entity.DeliveryStatusEntity;

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
