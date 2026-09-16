package co.cobre.notifications.boot;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Base class for boot tests with singleton Testcontainers: PostgreSQL and ElasticMQ.
 * Generates a temporary RSA public key file and configures SQS/JWT properties.
 * Webhook and JWT audience/client-claim properties come from production YAML.
 */
public abstract class BootTestSupport {

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
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize boot test containers", e);
        }
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

        // Encode public key as PEM
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        String pem = "-----BEGIN PUBLIC KEY-----\n" +
                     base64 + "\n" +
                     "-----END PUBLIC KEY-----";

        // Write to temporary file
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
            String elasticMQEndpoint = String.format("http://localhost:%d", ELASTICMQ.getMappedPort(9324));
            SqsAsyncClient sqs = SqsAsyncClient.builder()
                .endpointOverride(new java.net.URI(elasticMQEndpoint))
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("local", "local")
                ))
                .build();

            sqs.createQueue(req -> req.queueName("account-events-boot")).get();
            sqs.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SQS queue in ElasticMQ", e);
        }
    }
}
