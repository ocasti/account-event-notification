package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.application.port.DeliveryClaim;
import co.cobre.notifications.application.port.DeliveryAttemptRepository;
import co.cobre.notifications.domain.DeliveryAttempt;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptJpaRepository;
import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptEntityMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DeliveryAttemptRepositoryAdapter implements DeliveryAttemptRepository {

    private final DeliveryAttemptJpaRepository jpaRepository;
    private final DeliveryAttemptEntityMapper mapper;

    public DeliveryAttemptRepositoryAdapter(
        DeliveryAttemptJpaRepository jpaRepository,
        DeliveryAttemptEntityMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(DeliveryAttempt attempt) {
        var entity = mapper.toEntity(attempt);
        jpaRepository.save(entity);
    }

    @Override
    public List<DeliveryAttempt> findByEvent(EventId eventId) {
        var sort = Sort.by(
            new Sort.Order(Sort.Direction.ASC, "cycle"),
            new Sort.Order(Sort.Direction.ASC, "attemptNumber")
        );
        return jpaRepository.findByEventId(eventId.value(), sort)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public List<DeliveryAttempt> claimDue(DeliveryClaim claim) {
        long leaseSeconds = claim.lease().getSeconds();

        var allClaimed = jpaRepository.claimDueAttempts(
            leaseSeconds,
            claim.limit()
        );

        var ids = capPerClient(allClaimed, claim.maxPerClient());
        if (ids.isEmpty()) {
            return List.of();
        }

        int rowsUpdated = jpaRepository.updateClaimedBatch(ids, claim.workerId());
        if (rowsUpdated == 0) {
            return List.of();
        }

        return reload(ids, claim.workerId());
    }

    private List<UUID> capPerClient(List<Map<String, Object>> rows, int maxPerClient) {
        Map<String, Integer> clientCount = new HashMap<>();
        return rows.stream()
            .filter(row -> {
                String clientId = (String) row.get("client_id");
                int count = clientCount.getOrDefault(clientId, 0);
                if (count < maxPerClient) {
                    clientCount.put(clientId, count + 1);
                    return true;
                }
                return false;
            })
            .map(row -> (UUID) row.get("id"))
            .toList();
    }

    private List<DeliveryAttempt> reload(List<UUID> ids, String workerId) {
        return jpaRepository.findAllById(ids).stream()
            .filter(entity -> workerId.equals(entity.getClaimedBy()) && entity.getExecutedAt() == null)
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public boolean recordResultIf(DeliveryAttempt executed, String claimedBy) {
        int rows = jpaRepository.recordResult(
            executed.id(),
            claimedBy,
            executed.executedAt().orElse(null),
            executed.responseStatus().orElse(null),
            executed.failureReason().orElse(null),
            executed.latency().map(d -> d.toMillis()).orElse(null)
        );
        return rows == 1;
    }

    @Override
    public Map<EventId, Integer> countByEvents(Collection<EventId> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }

        List<String> eventIdValues = eventIds.stream()
            .map(id -> id.value())
            .toList();

        var results = jpaRepository.countByEventIds(eventIdValues);
        Map<EventId, Integer> counts = new HashMap<>();

        for (Object[] row : results) {
            String eventId = (String) row[0];
            Long count = (Long) row[1];
            counts.put(
                new EventId(eventId),
                count.intValue()
            );
        }

        return counts;
    }
}
