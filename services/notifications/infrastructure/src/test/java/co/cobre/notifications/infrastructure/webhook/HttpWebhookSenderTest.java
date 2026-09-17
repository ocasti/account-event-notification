package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpWebhookSenderTest {

    static final Instant FIXED_TIME = Instant.parse("2025-09-15T10:30:45.123000000Z");
    static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneId.of("UTC"));

    @Test
    void shouldSendPostRequestWhenDeliveringWebhookEvent() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);
        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();
        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(content().contentType("application/json"))
            .andRespond(withSuccess());

        sender.send(subscription, event, attempt);

        mockServer.verify();
    }

    @Test
    void shouldIncludeEventTimestampHeaderWhenSendingWebhook() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);
        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();
        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andExpect(header("event-timestamp", FIXED_TIME.toString()))
            .andRespond(withSuccess());

        sender.send(subscription, event, attempt);

        mockServer.verify();
    }

    @Test
    void shouldIncludeEventSignatureHeaderWhenKeyPresent() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);
        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();
        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andExpect(header("event-signature", org.hamcrest.Matchers.notNullValue()))
            .andRespond(withSuccess());

        sender.send(subscription, event, attempt);

        mockServer.verify();
    }

    @Test
    void shouldNotIncludeSignatureHeaderWhenNoKey() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);
        var subscription = createSubscription("https://api.example.com/hook", null);
        var event = createEvent();
        var attempt = createAttempt();
        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andExpect(request -> assertThat(request.getHeaders().get("event-signature")).isNull())
            .andRespond(withSuccess());

        sender.send(subscription, event, attempt);

        mockServer.verify();
    }

    @Test
    void shouldIncludeCobreEventIdHeaderWhenSendingWebhook() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);
        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();
        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andExpect(header("x-cobre-event-id", event.eventId().value()))
            .andRespond(withSuccess());

        sender.send(subscription, event, attempt);

        mockServer.verify();
    }

    @Test
    void shouldIncludeCobreAttemptHeaderWhenSendingWebhook() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);
        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();
        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andExpect(header("x-cobre-attempt", "1"))
            .andRespond(withSuccess());

        sender.send(subscription, event, attempt);

        mockServer.verify();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statusClassificationRows")
    void shouldClassifyOutcomeWhenServerReturnsStatus(
        String rowName, int status, Class<? extends DeliveryOutcome> expectedType,
        int expectedResponseStatus, String expectedReason
    ) throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);
        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();
        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andRespond(withStatus(org.springframework.http.HttpStatusCode.valueOf(status)));

        var outcome = sender.send(subscription, event, attempt);

        assertThat(outcome).isInstanceOf(expectedType);
        assertThat(responseStatusOf(outcome)).contains(expectedResponseStatus);
        assertThat(reasonOf(outcome)).isEqualTo(expectedReason);
        assertThat(latencyOf(outcome).toNanos()).isGreaterThanOrEqualTo(0);
    }

    private static Stream<Arguments> statusClassificationRows() {
        return Stream.of(
            Arguments.of("200 classifies as success", 200, DeliveryOutcome.Success.class, 200, null),
            Arguments.of("503 classifies as transient failure", 503, DeliveryOutcome.TransientFailure.class, 503, "HTTP 503"),
            Arguments.of("429 classifies as transient failure", 429, DeliveryOutcome.TransientFailure.class, 429, "HTTP 429"),
            Arguments.of("408 classifies as transient failure", 408, DeliveryOutcome.TransientFailure.class, 408, "HTTP 408"),
            Arguments.of("404 classifies as permanent failure", 404, DeliveryOutcome.PermanentFailure.class, 404, "client rejected: 404"),
            Arguments.of("400 classifies as permanent failure", 400, DeliveryOutcome.PermanentFailure.class, 400, "client rejected: 400"),
            Arguments.of("100 classifies as unclassified transient failure", 100, DeliveryOutcome.TransientFailure.class, 100, "unexpected: 100")
        );
    }

    @Test
    void shouldReturnTransientFailureWhenConnectionErrorOccurs() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);
        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();
        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andRespond(request -> {
                throw new java.io.IOException("connection refused");
            });

        var outcome = sender.send(subscription, event, attempt);

        assertThat(outcome).isInstanceOf(DeliveryOutcome.TransientFailure.class);
        assertThat(responseStatusOf(outcome)).isEmpty();
        assertThat(reasonOf(outcome)).isNotBlank();
    }

    @Test
    void shouldReturnPermanentFailureWhenUrlValidationFails() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var validator = new WebhookUrlValidator(
            new WebhookProperties(
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                true,
                List.of(),
                Duration.ofSeconds(30)
            ),
            host -> {
                try {
                    return List.of(InetAddress.getByName("10.0.0.5"));
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            }
        );
        var signer = new WebhookSigner(FIXED_CLOCK);
        var mapper = new WebhookPayloadMapper(JsonMapper.builder().build());
        var sender = new HttpWebhookSender(restClient, signer, mapper, validator);
        var subscription = createSubscription("https://private.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();
        mockServer.expect(org.springframework.test.web.client.ExpectedCount.never(), requestTo(org.hamcrest.Matchers.any(String.class)))
            .andRespond(withSuccess());

        var outcome = sender.send(subscription, event, attempt);

        assertThat(outcome).isInstanceOf(DeliveryOutcome.PermanentFailure.class);
        assertThat(responseStatusOf(outcome)).contains(0);
        assertThat(reasonOf(outcome)).isEqualTo("invalid webhook url");
    }

    private static Optional<Integer> responseStatusOf(DeliveryOutcome outcome) {
        if (outcome instanceof DeliveryOutcome.Success success) {
            return Optional.of(success.responseStatus());
        }
        if (outcome instanceof DeliveryOutcome.TransientFailure transientFailure) {
            return transientFailure.responseStatus();
        }
        if (outcome instanceof DeliveryOutcome.PermanentFailure permanentFailure) {
            return Optional.of(permanentFailure.responseStatus());
        }
        throw new IllegalStateException("unexpected outcome: " + outcome);
    }

    private static String reasonOf(DeliveryOutcome outcome) {
        if (outcome instanceof DeliveryOutcome.TransientFailure transientFailure) {
            return transientFailure.reason();
        }
        if (outcome instanceof DeliveryOutcome.PermanentFailure permanentFailure) {
            return permanentFailure.reason();
        }
        return null;
    }

    private static Duration latencyOf(DeliveryOutcome outcome) {
        if (outcome instanceof DeliveryOutcome.Success success) {
            return success.latency();
        }
        if (outcome instanceof DeliveryOutcome.TransientFailure transientFailure) {
            return transientFailure.latency();
        }
        return ((DeliveryOutcome.PermanentFailure) outcome).latency();
    }

    private HttpWebhookSender createSender(RestClient restClient) {
        var props = new WebhookProperties(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            true,
            List.of("api.example.com"),
            Duration.ofSeconds(30)
        );
        var validator = new WebhookUrlValidator(props, host -> {
            try {
                return List.of(InetAddress.getByName("93.184.216.34"));
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        });
        var signer = new WebhookSigner(FIXED_CLOCK);
        var mapper = new WebhookPayloadMapper(JsonMapper.builder().build());
        return new HttpWebhookSender(restClient, signer, mapper, validator);
    }

    private Subscription createSubscription(String url, String signatureKey) throws Exception {
        return new Subscription(
            "sub-1",
            new ClientId("client-1"),
            java.util.Set.of(new EventKey("test.event")),
            new WebhookUrl(new URI(url)),
            Optional.empty(),
            Optional.ofNullable(signatureKey),
            true,
            Instant.now()
        );
    }

    private NotificationEvent createEvent() {
        return new NotificationEvent(
            new EventId("event-1"),
            new ClientId("client-1"),
            new EventKey("test.event"),
            "{\"data\":\"test\"}",
            Instant.now(),
            Instant.now(),
            DeliveryStatus.PENDING,
            Optional.of("sub-1"),
            0,
            Optional.empty()
        );
    }

    private DeliveryAttempt createAttempt() {
        return DeliveryAttempt.first(new EventId("event-1"), 0, Instant.now(), AttemptOrigin.SYSTEM);
    }
}
