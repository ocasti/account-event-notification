package co.cobre.notifications.acceptance;

import co.cobre.notifications.infrastructure.persistence.AttemptOriginEntity;
import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptEntity;
import co.cobre.notifications.infrastructure.persistence.DeliveryAttemptJpaRepository;
import co.cobre.notifications.infrastructure.persistence.DeliveryStatusEntity;
import co.cobre.notifications.infrastructure.persistence.NotificationEventEntity;
import co.cobre.notifications.infrastructure.persistence.NotificationEventJpaRepository;
import co.cobre.notifications.infrastructure.security.RestTestSecurityConfig;
import co.cobre.notifications.infrastructure.worker.AccountEventMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance test reproducing the RFC's main criterion (docs/01-system-design.html, section 19,
 * "Acceptance criteria"): publish the ten reference-dataset events
 * (docs/notification_events.json) on the queue and verify the system ends with exactly
 * EVT003, EVT005 and EVT009 in FAILED (WireMock answers 503 for those event ids, per
 * deploy/local/wiremock/mappings/webhook-503.json) and the other seven in COMPLETED, each
 * with a coherent attempt history. A second case replays EVT003 as a different client and
 * checks the new cycle also ends FAILED.
 *
 * <p>Runs {@code api}, {@code worker} and {@code local} together in one JVM (listener,
 * scheduler and API share the same context): nothing in NotificationsApplication or
 * infrastructure.config excludes that combination — only SchedulingConfig, DeliveryScheduler,
 * AccountEventListener and DueAttemptsGaugeUpdater are {@code @Profile("worker")}, and none of
 * them (nor SecurityConfig/NotificationEventController) excludes "api" when "worker" is also
 * active.
 *
 * <p>Containers: PostgreSQL and ElasticMQ are owned by this test (static singletons);
 * the queue "account-events-acceptance" is created in its static initializer.
 * WireMock is this test's own singleton container, seeded with the mappings under
 * deploy/local/wiremock/mappings — resolved to an absolute path by walking up from
 * {@code user.dir} so it works regardless of which module directory the surefire-forked
 * JVM starts from.
 *
 * <p>JWT signing reuses {@link RestTestSecurityConfig} (same package, same test sources):
 * this test needs to sign tokens for CLIENT001/CLIENT002, which only RestTestSecurityConfig's
 * RSA key pair can do.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"api", "worker", "local"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReferenceDatasetAcceptanceIT {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String QUEUE_NAME = "account-events-acceptance";

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withReuse(false);

    private static final GenericContainer<?> ELASTICMQ =
        new GenericContainer<>("softwaremill/elasticmq-native:1.6.12")
            .withExposedPorts(9324);

    private static final GenericContainer<?> WIREMOCK = new GenericContainer<>("wiremock/wiremock:3.13.1")
        .withExposedPorts(8080)
        .withCommand("--port", "8080", "--disable-banner", "--max-request-journal-entries", "5000")
        .withCopyFileToContainer(
            MountableFile.forHostPath(resolveRepoPath("deploy/local/wiremock/mappings").toString()),
            "/home/wiremock/mappings")
        .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200));

    static {
        POSTGRES.start();
        ELASTICMQ.start();
        WIREMOCK.start();
        createSqsQueue();
    }

    /**
     * Create SQS queue in ElasticMQ before starting the Spring context.
     * This ensures the AccountEventListener (in worker profile) can find the queue.
     */
    private static void createSqsQueue() {
        try {
            String elasticMQEndpoint = String.format("http://localhost:%d", ELASTICMQ.getMappedPort(9324));
            SqsAsyncClient sqs = SqsAsyncClient.builder()
                .endpointOverride(new URI(elasticMQEndpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("local", "local")
                ))
                .build();

            sqs.createQueue(req -> req.queueName(QUEUE_NAME)).get();
            sqs.close();
        } catch (URISyntaxException | ExecutionException e) {
            throw new RuntimeException("Failed to create SQS queue in ElasticMQ", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to create SQS queue in ElasticMQ", e);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SqsTemplate sqsTemplate;

    @Autowired
    private NotificationEventJpaRepository notificationEventJpaRepository;

    @Autowired
    private DeliveryAttemptJpaRepository deliveryAttemptJpaRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        String elasticMQEndpoint = String.format("http://localhost:%d", ELASTICMQ.getMappedPort(9324));
        registry.add("spring.cloud.aws.sqs.endpoint", () -> elasticMQEndpoint);
        registry.add("spring.cloud.aws.region.static", () -> "us-east-1");
        registry.add("spring.cloud.aws.credentials.access-key", () -> "local");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "local");
        registry.add("notifications.sqs.queue-name", () -> QUEUE_NAME);
        registry.add("notifications.jwt.public-key", () -> "file:" + RestTestSecurityConfig.publicKeyFile());
        registry.add("spring.flyway.placeholders.webhookUrl",
            () -> String.format("http://%s:%d/webhook", WIREMOCK.getHost(), WIREMOCK.getMappedPort(8080)));
        registry.add("notifications.webhook.allowlist", WIREMOCK::getHost);
        registry.add("notifications.webhook.require-https", () -> "false");
        registry.add("notifications.retry.base-delay", () -> "1s");
        registry.add("notifications.retry.max-delay", () -> "2s");
        registry.add("notifications.retry.jitter-ratio", () -> "0");
    }

    @Test
    @Order(1)
    void referenceDatasetEndsWithExpectedDeliveryStatuses() throws Exception {
        List<ReferenceEvent> events = loadReferenceEvents();
        assertThat(events).hasSize(10);

        for (ReferenceEvent event : events) {
            var message = new AccountEventMessage(
                event.eventId(), event.eventType(), event.clientId(), event.content(), event.deliveryDate()
            );
            sqsTemplate.send(QUEUE_NAME, JSON_MAPPER.writeValueAsString(message));
        }

        List<String> eventIds = events.stream().map(ReferenceEvent::eventId).toList();
        awaitNoPendingOrRetrying(eventIds, Duration.ofSeconds(90));

        Map<String, NotificationEventEntity> byId = notificationEventJpaRepository.findAllById(eventIds).stream()
            .collect(Collectors.toMap(NotificationEventEntity::getEventId, e -> e));

        for (ReferenceEvent event : events) {
            NotificationEventEntity entity = byId.get(event.eventId());
            assertThat(entity)
                .as("event %s must be registered", event.eventId())
                .isNotNull();
            assertThat(entity.getStatus())
                .as("delivery_status of %s", event.eventId())
                .isEqualTo(DeliveryStatusEntity.valueOf(event.deliveryStatus().toUpperCase()));

            List<DeliveryAttemptEntity> attempts = deliveryAttemptJpaRepository.findByEventId(
                event.eventId(), Sort.by(Sort.Direction.ASC, "attemptNumber")
            );

            if ("completed".equals(event.deliveryStatus())) {
                assertThat(attempts).as("attempts of completed %s", event.eventId()).hasSize(1);
                assertThat(attempts.get(0).getCycle()).isZero();
                assertThat(attempts.get(0).getAttemptNumber()).isEqualTo(1);
                assertThat(attempts.get(0).getResponseStatus()).isEqualTo(200);
            } else {
                assertThat(attempts).as("attempts of failed %s", event.eventId()).hasSize(5);
                for (int i = 0; i < attempts.size(); i++) {
                    DeliveryAttemptEntity attempt = attempts.get(i);
                    assertThat(attempt.getCycle()).isZero();
                    assertThat(attempt.getAttemptNumber()).isEqualTo(i + 1);
                    assertThat(attempt.getResponseStatus()).isEqualTo(503);
                }
            }
        }

        assertThat(countWiremockWebhookRequests())
            .as("WireMock POST /webhook count: 7 completed x1 + 3 failed x5")
            .isEqualTo(22);

        String client001Token = RestTestSecurityConfig.token("CLIENT001");

        // EVT003 belongs to CLIENT002: CLIENT001's token must not be able to see it.
        mockMvc.perform(get("/notification_events/EVT003")
                .header("Authorization", "Bearer " + client001Token))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/notification_events")
                .header("Authorization", "Bearer " + client001Token)
                .param("limit", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.event_id == 'EVT003')]").doesNotExist());
    }

    @Test
    @Order(2)
    void replayOfFailedEventOpensNewCycleAndEndsFailedAgain() throws Exception {
        long requestsBeforeReplay = countWiremockWebhookRequests();

        String client002Token = RestTestSecurityConfig.token("CLIENT002");

        mockMvc.perform(post("/notification_events/EVT003/replay")
                .header("Authorization", "Bearer " + client002Token))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.event_id").value("EVT003"))
            .andExpect(jsonPath("$.cycle").value(1))
            .andExpect(jsonPath("$.delivery_status").value("pending"));

        awaitNoPendingOrRetrying(List.of("EVT003"), Duration.ofSeconds(30));

        NotificationEventEntity entity = notificationEventJpaRepository.findById("EVT003").orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(DeliveryStatusEntity.FAILED);
        assertThat(entity.getCycle()).isEqualTo(1);

        List<DeliveryAttemptEntity> attempts = deliveryAttemptJpaRepository.findByEventId(
            "EVT003", Sort.by(Sort.Direction.ASC, "cycle").and(Sort.by(Sort.Direction.ASC, "attemptNumber"))
        );
        assertThat(attempts).hasSize(10);

        List<DeliveryAttemptEntity> cycle0 = attempts.stream().filter(a -> a.getCycle() == 0).toList();
        List<DeliveryAttemptEntity> cycle1 = attempts.stream().filter(a -> a.getCycle() == 1).toList();
        assertThat(cycle0).hasSize(5);
        assertThat(cycle1).hasSize(5);
        attempts.forEach(a -> assertThat(a.getResponseStatus()).isEqualTo(503));

        assertThat(cycle1.get(0).getOrigin()).isEqualTo(AttemptOriginEntity.REPLAY);
        for (int i = 1; i < cycle1.size(); i++) {
            assertThat(cycle1.get(i).getOrigin()).isEqualTo(AttemptOriginEntity.SYSTEM);
        }

        assertThat(countWiremockWebhookRequests() - requestsBeforeReplay)
            .as("cycle 1 must have delivered exactly 5 more POSTs to /webhook")
            .isEqualTo(5);

        mockMvc.perform(get("/notification_events/EVT003")
                .header("Authorization", "Bearer " + client002Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.delivery_status").value("failed"))
            .andExpect(jsonPath("$.attempts_count").value(10))
            .andExpect(jsonPath("$.attempts[5].cycle").value(1))
            .andExpect(jsonPath("$.attempts[5].attempt_number").value(1))
            .andExpect(jsonPath("$.attempts[5].origin").value("replay"));
    }

    private List<ReferenceEvent> loadReferenceEvents() throws IOException {
        Path datasetPath = resolveRepoPath("docs/notification_events.json");
        String json = Files.readString(datasetPath, StandardCharsets.UTF_8);
        return JSON_MAPPER.readValue(json, ReferenceDataset.class).events();
    }

    private void awaitNoPendingOrRetrying(List<String> eventIds, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            List<NotificationEventEntity> current = notificationEventJpaRepository.findAllById(eventIds);
            boolean settled = current.size() == eventIds.size() && current.stream().noneMatch(e ->
                e.getStatus() == DeliveryStatusEntity.PENDING || e.getStatus() == DeliveryStatusEntity.RETRYING);
            if (settled) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out after " + timeout + " waiting for " + eventIds
            + " to leave PENDING/RETRYING");
    }

    private long countWiremockWebhookRequests() throws IOException, InterruptedException {
        String url = String.format(
            "http://%s:%d/__admin/requests/count", WIREMOCK.getHost(), WIREMOCK.getMappedPort(8080));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"method\":\"POST\",\"urlPath\":\"/webhook\"}"))
            .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("WireMock admin /requests/count status").isEqualTo(200);
        return JSON_MAPPER.readValue(response.body(), RequestCountResponse.class).count();
    }

    /**
     * Walks up from {@code user.dir} looking for {@code relative}, so the mapping directory
     * and the reference dataset resolve correctly whether surefire forks from the repo root
     * or from a module's own base directory.
     */
    private static Path resolveRepoPath(String relative) {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
            "Could not resolve '" + relative + "' walking up from user.dir=" + System.getProperty("user.dir"));
    }

    private record ReferenceEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("content") String content,
        @JsonProperty("delivery_date") Instant deliveryDate,
        @JsonProperty("delivery_status") String deliveryStatus,
        @JsonProperty("client_id") String clientId
    ) {}

    private record ReferenceDataset(@JsonProperty("events") List<ReferenceEvent> events) {}

    private record RequestCountResponse(@JsonProperty("count") long count) {}
}
