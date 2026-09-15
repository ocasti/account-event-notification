package co.cobre.notifications.infrastructure.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.placeholders.webhookUrl=https://example.test/webhook",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
@Import(PersistenceTestConfig.class)
public class PersistenceTestSupport {

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}
