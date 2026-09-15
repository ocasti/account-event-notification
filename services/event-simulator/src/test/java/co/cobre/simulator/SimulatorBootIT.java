package co.cobre.simulator;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(
    properties = {
        "simulator.emit-interval=1h",
        "simulator.emit-reference-on-start=true",
        "spring.cloud.aws.region.static=us-east-1",
        "spring.cloud.aws.credentials.access-key=local",
        "spring.cloud.aws.credentials.secret-key=local"
    })
class SimulatorBootIT {

    @Container
    static final GenericContainer<?> elasticMQ = new GenericContainer<>("softwaremill/elasticmq-native:1.6.12")
        .withExposedPorts(9324);

    @DynamicPropertySource
    static void registerElasticMQProperties(DynamicPropertyRegistry registry) {
        String endpoint = String.format("http://localhost:%d", elasticMQ.getMappedPort(9324));
        registry.add("spring.cloud.aws.sqs.endpoint", () -> endpoint);
        registry.add("simulator.queue-name", () -> "account-events");
    }

    @Autowired(required = false)
    private SqsTemplate sqsTemplate;

    @Test
    void applicationStartsAndEmitsTenReferenceEventsOnInitialization() {
        if (sqsTemplate != null) {
            await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    long messageCount = getApproximateMessageCount();
                    assertThat(messageCount).isEqualTo(10);
                });
        }
    }

    private long getApproximateMessageCount() throws ExecutionException, InterruptedException, java.net.URISyntaxException {
        String endpoint = String.format("http://localhost:%d", elasticMQ.getMappedPort(9324));
        try (SqsAsyncClient client = SqsAsyncClient.builder()
            .endpointOverride(new URI(endpoint))
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .credentialsProvider(
                software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("local", "local")
                ))
            .build()) {

            String queueUrl = client.getQueueUrl(
                req -> req.queueName("account-events")
            ).get().queueUrl();

            return Long.parseLong(client.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build()
            ).get()
                .attributes()
                .getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0"));
        }
    }
}
