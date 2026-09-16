package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventId;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class DeliveryAttemptEntityMapper {

    public DeliveryAttempt toDomain(DeliveryAttemptEntity entity) {
        return new DeliveryAttempt(
            entity.getId(),
            new EventId(entity.getEventId()),
            entity.getCycle(),
            entity.getAttemptNumber(),
            entity.getNextAttemptAt(),
            Optional.ofNullable(entity.getClaimedAt()),
            Optional.ofNullable(entity.getClaimedBy()),
            Optional.ofNullable(entity.getExecutedAt()),
            Optional.ofNullable(entity.getResponseStatus()),
            Optional.ofNullable(entity.getFailureReason()),
            entity.getLatencyMs() != null ? Optional.of(Duration.ofMillis(entity.getLatencyMs())) : Optional.empty(),
            mapOriginToDomain(entity.getOrigin())
        );
    }

    public DeliveryAttemptEntity toEntity(DeliveryAttempt domain) {
        var entity = new DeliveryAttemptEntity();
        entity.setId(domain.id());
        entity.setEventId(domain.eventId().value());
        entity.setCycle(domain.cycle());
        entity.setAttemptNumber(domain.attemptNumber());
        entity.setNextAttemptAt(domain.nextAttemptAt());
        entity.setClaimedAt(domain.claimedAt().orElse(null));
        entity.setClaimedBy(domain.claimedBy().orElse(null));
        entity.setExecutedAt(domain.executedAt().orElse(null));
        entity.setResponseStatus(domain.responseStatus().orElse(null));
        entity.setFailureReason(domain.failureReason().orElse(null));
        entity.setLatencyMs(domain.latency().map(Duration::toMillis).orElse(null));
        entity.setOrigin(mapOriginToEntity(domain.origin()));
        return entity;
    }

    private AttemptOrigin mapOriginToDomain(AttemptOriginEntity entity) {
        return switch (entity) {
            case SYSTEM -> AttemptOrigin.SYSTEM;
            case REPLAY -> AttemptOrigin.REPLAY;
        };
    }

    private AttemptOriginEntity mapOriginToEntity(AttemptOrigin domain) {
        return switch (domain) {
            case SYSTEM -> AttemptOriginEntity.SYSTEM;
            case REPLAY -> AttemptOriginEntity.REPLAY;
        };
    }
}
