package co.cobre.notifications.infrastructure.persistence.adapter;

import co.cobre.notifications.application.port.out.SubscriptionRepository;
import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.EventKey;
import co.cobre.notifications.domain.model.Subscription;
import co.cobre.notifications.infrastructure.persistence.jpa.SubscriptionJpaRepository;
import co.cobre.notifications.infrastructure.persistence.mapper.SubscriptionEntityMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository jpaRepository;
    private final SubscriptionEntityMapper mapper;

    public SubscriptionRepositoryAdapter(
        SubscriptionJpaRepository jpaRepository,
        SubscriptionEntityMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Subscription> findActive(ClientId clientId, EventKey eventKey) {
        return jpaRepository.findByClientIdAndActiveTrue(clientId.value())
            .stream()
            .map(mapper::toDomain)
            .filter(sub -> sub.matches(eventKey))
            .findFirst();
    }

    @Override
    public Optional<Subscription> findById(String subscriptionId) {
        return jpaRepository.findById(subscriptionId)
            .map(mapper::toDomain);
    }
}
