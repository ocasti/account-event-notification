package co.cobre.notifications.boot;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

/**
 * Base class for boot tests with singleton Testcontainers: PostgreSQL and ElasticMQ.
 * Generates a temporary RSA public key file and configures SQS/JWT properties.
 * Webhook and JWT audience/client-claim properties come from production YAML.
 */
public abstract class BootTestSupport {

    protected static final String QUEUE_NAME = "account-events-boot";

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withReuse(false);

    protected static final GenericContainer<?> ELASTICMQ =
        new GenericContainer<>("softwaremill/elasticmq-native:1.6.12")
            .withExposedPorts(9324);

    public static Path publicKeyPath;

    static {
        try {
            POSTGRES.start();
            ELASTICMQ.start();
            publicKeyPath = generateRsaPublicKeyFile();
            createSqsQueue();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to initialize boot test containers", e);
        }
    }

    /**
     * Registers the SQS, Flyway and JWT properties that every boot profile needs.
     * Subclasses call it from their own {@code @DynamicPropertySource} method.
     */
    protected static void registerBootProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.sqs.endpoint", BootTestSupport::elasticMqEndpoint);
        registry.add("spring.cloud.aws.region.static", () -> "us-east-1");
        registry.add("spring.cloud.aws.credentials.access-key", () -> "local");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "local");
        registry.add("notifications.sqs.queue-name", () -> QUEUE_NAME);
        registry.add("spring.flyway.placeholders.webhookUrl", () -> "https://example.test/webhook");
        registry.add("notifications.jwt.public-key", () -> "file:" + publicKeyPath);
    }

    private static String elasticMqEndpoint() {
        return String.format("http://localhost:%d", ELASTICMQ.getMappedPort(9324));
    }

    /**
     * Generate RSA public key and write to temporary file in PEM format.
     *
     * @return path to the public key file
     * @throws NoSuchAlgorithmException if RSA algorithm is not available
     * @throws IOException if writing the file fails
     */
    private static Path generateRsaPublicKeyFile() throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();

        byte[] encoded = publicKey.getEncoded();
        String encodedPublicKey = Base64.getEncoder().encodeToString(encoded);
        String pem = "-----BEGIN PUBLIC KEY-----\n" +
                     encodedPublicKey + "\n" +
                     "-----END PUBLIC KEY-----";

        Path tempFile = Files.createTempFile("boot-test-public-key", ".pem");
        Files.write(tempFile, pem.getBytes());
        tempFile.toFile().deleteOnExit();

        return tempFile;
    }

    /**
     * Create SQS queue in ElasticMQ before starting the Spring context.
     * This ensures the AccountEventListener (in worker profile) can find the queue.
     */
    private static void createSqsQueue() {
        try {
            SqsAsyncClient sqs = SqsAsyncClient.builder()
                .endpointOverride(new URI(elasticMqEndpoint()))
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
}
