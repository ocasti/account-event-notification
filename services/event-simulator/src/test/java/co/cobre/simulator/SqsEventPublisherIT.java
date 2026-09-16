package co.cobre.simulator;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = SimulatorApplication.class,
    properties = {
        "simulator.queue-name=test-queue",
        "simulator.emit-interval=1h",
        "simulator.emit-reference-on-start=false",
        "spring.cloud.aws.region.static=us-east-1",
        "spring.cloud.aws.credentials.access-key=local",
        "spring.cloud.aws.credentials.secret-key=local"
    })
class SqsEventPublisherIT {

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
    private SqsEventPublisher publisher;

    @Test
    void publishLeavesMessageInQueueWithCorrectContract() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        ReferenceEvent event = new ReferenceEvent(
            "EVT001",
            "credit_card_payment",
            "CLIENT001",
            "Payment received",
            Instant.parse("2024-03-15T09:30:22Z")
        );

        publisher.publish(event);

        var messageOpt = sqsTemplate.receive("test-queue", String.class);
        assertThat(messageOpt).isPresent();
        Message<?> message = messageOpt.get();
        assertThat(message.getPayload()).isNotNull();

        String payload = message.getPayload().toString();
        Map<String, Object> parsed = mapper.readValue(payload, Map.class);
        assertThat(parsed.get("event_id")).isEqualTo("EVT001");
        assertThat(parsed.get("event_type")).isEqualTo("credit_card_payment");
        assertThat(parsed.get("client_id")).isEqualTo("CLIENT001");
        assertThat(parsed.get("content")).isEqualTo("Payment received");
        assertThat(parsed.get("occurred_at")).isEqualTo("2024-03-15T09:30:22Z");
    }
}
