package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.infrastructure.persistence.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, String> {
    List<SubscriptionEntity> findByClientIdAndActiveTrue(String clientId);

    List<SubscriptionEntity> findByClientIdAndActiveTrueOrderByCreatedAtAsc(String clientId);
}
