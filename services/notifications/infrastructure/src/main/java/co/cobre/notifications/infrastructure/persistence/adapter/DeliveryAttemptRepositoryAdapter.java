package co.cobre.notifications.infrastructure.persistence.adapter;

import co.cobre.notifications.application.port.out.DeliveryClaim;
import co.cobre.notifications.application.port.out.DeliveryAttemptRepository;
import co.cobre.notifications.domain.model.DeliveryAttempt;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.infrastructure.persistence.jpa.DeliveryAttemptJpaRepository;
import co.cobre.notifications.infrastructure.persistence.mapper.DeliveryAttemptEntityMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository adapter for delivery attempts.
 */
@Repository
public class DeliveryAttemptRepositoryAdapter implements DeliveryAttemptRepository {

    private final DeliveryAttemptJpaRepository jpaRepository;
    private final DeliveryAttemptEntityMapper mapper;

    /**
     * Creates a new delivery attempt repository adapter.
     */
    public DeliveryAttemptRepositoryAdapter(
        DeliveryAttemptJpaRepository jpaRepository,
        DeliveryAttemptEntityMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(DeliveryAttempt attempt) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<DeliveryAttempt> claimDue(DeliveryClaim claim) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean recordResultIf(DeliveryAttempt executed, String claimedBy) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<DeliveryAttempt> findByEvent(EventId eventId) {
        throw new UnsupportedOperationException("not implemented");
    }
}
