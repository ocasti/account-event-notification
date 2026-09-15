package co.cobre.notifications.infrastructure.messaging;

import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import co.cobre.notifications.infrastructure.metrics.DeliveryMetrics;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@Testcontainers
@SpringBootTest(classes = {AccountEventListener.class, AccountEventMessageMapper.class, MessagingTestConfig.class},
    properties = {
        "spring.profiles.active=worker",
        "notifications.sqs.queue-name=account-events-test",
        "spring.cloud.aws.region.static=us-east-1",
        "spring.cloud.aws.credentials.access-key=local",
        "spring.cloud.aws.credentials.secret-key=local"
    })
class AccountEventListenerIT {

    @Container
    static final GenericContainer<?> elasticMQ = new GenericContainer<>("softwaremill/elasticmq-native:1.6.12")
        .withExposedPorts(9324);

    @DynamicPropertySource
    static void registerElasticMQProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.sqs.endpoint",
            () -> String.format("http://localhost:%d", elasticMQ.getMappedPort(9324)));
    }

    @Autowired
    private SqsTemplate sqsTemplate;

    @Autowired
    private RegisterNotificationEvent registerNotificationEvent;

    @Autowired
    private DeliveryMetrics deliveryMetrics;

    @Test
    void listenAndRegisterEvent() {
        String queueName = "account-events-test";
        String messageJson = """
            {
                "event_id": "EVT001",
                "event_type": "account.created",
                "client_id": "CLIENT123",
                "content": "account was created",
                "occurred_at": "2025-01-01T10:00:00Z"
            }
            """;

        sqsTemplate.send(queueName, messageJson);

        verify(registerNotificationEvent, timeout(15000))
            .register(argThat(cmd -> cmd.eventId().value().equals("EVT001")));
    }

    @Test
    void listenDuplicateEventOnlyRegistersOnce() {
        String queueName = "account-events-test";
        String messageJson = """
            {
                "event_id": "EVT002",
                "event_type": "account.updated",
                "client_id": "CLIENT456",
                "content": "account was updated",
                "occurred_at": "2025-01-01T11:00:00Z"
            }
            """;

        sqsTemplate.send(queueName, messageJson);
        sqsTemplate.send(queueName, messageJson);

        verify(registerNotificationEvent, timeout(15000).times(2))
            .register(argThat(cmd -> cmd.eventId().value().equals("EVT002")));
    }
}
