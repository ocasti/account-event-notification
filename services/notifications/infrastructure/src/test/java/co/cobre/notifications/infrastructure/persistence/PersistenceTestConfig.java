package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.infrastructure.persistence.adapter.DeliveryAttemptRepositoryAdapter;
import co.cobre.notifications.infrastructure.persistence.adapter.NotificationEventRepositoryAdapter;
import co.cobre.notifications.infrastructure.persistence.adapter.SubscriptionRepositoryAdapter;
import co.cobre.notifications.infrastructure.persistence.mapper.DeliveryAttemptEntityMapper;
import co.cobre.notifications.infrastructure.persistence.mapper.NotificationEventEntityMapper;
import co.cobre.notifications.infrastructure.persistence.mapper.SubscriptionEntityMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@TestConfiguration
public class PersistenceTestConfig {

    @Bean
    public Clock clock() {
        return Clock.fixed(Instant.parse("2024-01-15T12:00:00Z"), ZoneId.of("UTC"));
    }

    @Bean
    public NotificationEventEntityMapper notificationEventEntityMapper() {
        return new NotificationEventEntityMapper();
    }

    @Bean
    public DeliveryAttemptEntityMapper deliveryAttemptEntityMapper() {
        return new DeliveryAttemptEntityMapper();
    }

    @Bean
    public SubscriptionEntityMapper subscriptionEntityMapper() {
        return new SubscriptionEntityMapper();
    }

    @Bean
    public NotificationEventRepositoryAdapter notificationEventRepositoryAdapter(
        co.cobre.notifications.infrastructure.persistence.jpa.NotificationEventJpaRepository jpaRepository,
        NotificationEventEntityMapper mapper
    ) {
        return new NotificationEventRepositoryAdapter(jpaRepository, mapper);
    }

    @Bean
    public DeliveryAttemptRepositoryAdapter deliveryAttemptRepositoryAdapter(
        co.cobre.notifications.infrastructure.persistence.jpa.DeliveryAttemptJpaRepository jpaRepository,
        DeliveryAttemptEntityMapper mapper
    ) {
        return new DeliveryAttemptRepositoryAdapter(jpaRepository, mapper);
    }

    @Bean
    public SubscriptionRepositoryAdapter subscriptionRepositoryAdapter(
        co.cobre.notifications.infrastructure.persistence.jpa.SubscriptionJpaRepository jpaRepository,
        SubscriptionEntityMapper mapper
    ) {
        return new SubscriptionRepositoryAdapter(jpaRepository, mapper);
    }
}
