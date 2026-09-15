package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.infrastructure.persistence.adapter.DeliveryAttemptRepositoryAdapter;
import co.cobre.notifications.infrastructure.persistence.adapter.NotificationEventRepositoryAdapter;
import co.cobre.notifications.infrastructure.persistence.adapter.SubscriptionRepositoryAdapter;
import co.cobre.notifications.infrastructure.persistence.mapper.DeliveryAttemptEntityMapper;
import co.cobre.notifications.infrastructure.persistence.mapper.NotificationEventEntityMapper;
import co.cobre.notifications.infrastructure.persistence.mapper.SubscriptionEntityMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.flyway.placeholders.webhookUrl=https://example.test/webhook", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@Import({NotificationEventRepositoryAdapter.class, DeliveryAttemptRepositoryAdapter.class, SubscriptionRepositoryAdapter.class, NotificationEventEntityMapper.class, DeliveryAttemptEntityMapper.class, SubscriptionEntityMapper.class})
public abstract class PersistenceTestSupport {
    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
