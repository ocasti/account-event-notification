package co.cobre.notifications.infrastructure.persistence;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.flyway.placeholders.webhookUrl=https://example.test/webhook", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationEventRepositoryAdapter.class, DeliveryAttemptRepositoryAdapter.class, SubscriptionRepositoryAdapter.class, NotificationEventEntityMapper.class, DeliveryAttemptEntityMapper.class, SubscriptionEntityMapper.class})
public abstract class PersistenceTestSupport {
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine").withReuse(false);

    static {
        POSTGRES.start();
    }
}
