package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.RegisterEventCommand;
import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@Testcontainers
@SpringBootTest(classes = {AccountEventListener.class, AccountEventMessageMapper.class, MessagingTestConfig.class},
    properties = {
        "spring.profiles.active=worker",
        "notifications.sqs.queue-name=" + AccountEventListenerIT.QUEUE_NAME,
        "spring.cloud.aws.region.static=us-east-1",
        "spring.cloud.aws.credentials.access-key=local",
        "spring.cloud.aws.credentials.secret-key=local"
    })
@ImportAutoConfiguration({
    io.awspring.cloud.autoconfigure.core.AwsAutoConfiguration.class,
    io.awspring.cloud.autoconfigure.core.CredentialsProviderAutoConfiguration.class,
    io.awspring.cloud.autoconfigure.core.RegionProviderAutoConfiguration.class,
    io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration.class
})
class AccountEventListenerIT {

    static final String QUEUE_NAME = "account-events-test";
    private static final long LISTENER_TIMEOUT_MS = 15_000;
    private static final Instant MESSAGE_OCCURRED_AT = Instant.parse("2025-01-01T10:00:00Z");
    private static final String MESSAGE_CONTENT = "account event";

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

    @Test
    void shouldRegisterEventWhenMessageArrivesOnQueue() {
        var messageJson = MessagingTestConfig.accountEventJson("EVT001", "account.created", "CLIENT123");
        var expectedCommand = new RegisterEventCommand(
            new EventId("EVT001"), new ClientId("CLIENT123"), new EventKey("account.created"),
            MESSAGE_CONTENT, MESSAGE_OCCURRED_AT
        );

        sqsTemplate.send(QUEUE_NAME, messageJson);

        verify(registerNotificationEvent, timeout(LISTENER_TIMEOUT_MS)).register(expectedCommand);
    }

    @Test
    void shouldForwardEachDeliveryToRegisterWhenSameMessageArrivesTwice() {
        var messageJson = MessagingTestConfig.accountEventJson("EVT002", "account.updated", "CLIENT456");
        var expectedCommand = new RegisterEventCommand(
            new EventId("EVT002"), new ClientId("CLIENT456"), new EventKey("account.updated"),
            MESSAGE_CONTENT, MESSAGE_OCCURRED_AT
        );

        sqsTemplate.send(QUEUE_NAME, messageJson);
        sqsTemplate.send(QUEUE_NAME, messageJson);

        verify(registerNotificationEvent, timeout(LISTENER_TIMEOUT_MS).times(2)).register(expectedCommand);
    }
}
