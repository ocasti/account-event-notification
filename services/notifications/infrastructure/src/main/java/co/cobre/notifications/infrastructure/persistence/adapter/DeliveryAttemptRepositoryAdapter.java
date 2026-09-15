package co.cobre.notifications.infrastructure.persistence.adapter;

import co.cobre.notifications.application.port.out.DeliveryClaim;
import co.cobre.notifications.application.port.out.DeliveryAttemptRepository;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.infrastructure.persistence.jpa.DeliveryAttemptJpaRepository;
import co.cobre.notifications.infrastructure.persistence.mapper.DeliveryAttemptEntityMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

        Map<String, Integer> clientCount = new HashMap<>();
        var limitedResults = allClaimed.stream()
            .filter(row -> {
                UUID id = (UUID) row.get("id");
                String clientId = (String) row.get("client_id");
                int count = clientCount.getOrDefault(clientId, 0);
                if (count < claim.maxPerClient()) {
                    clientCount.put(clientId, count + 1);
                    return true;
                }
                return false;
            })
            .toList();

        if (limitedResults.isEmpty()) {
            return List.of();
        }

        var ids = limitedResults.stream()
            .map(row -> (UUID) row.get("id"))
            .collect(Collectors.toSet());

        jpaRepository.updateClaimedBatch(ids, claim.workerId());

        return jpaRepository.findAllById(ids)
            .stream()
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
}
