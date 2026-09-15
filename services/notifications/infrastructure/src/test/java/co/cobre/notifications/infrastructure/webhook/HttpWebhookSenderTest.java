package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpWebhookSenderTest {

    static final Instant FIXED_TIME = Instant.parse("2025-09-15T10:30:45.123000000Z");
    static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneId.of("UTC"));

    @Test
    void shouldSendPostWithJsonContentType() throws Exception {
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
    void shouldIncludeEventTimestampHeader() throws Exception {
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
            .andExpect(request -> {
                assertThat(request.getHeaders().get("event-signature")).isNull();
            })
            .andRespond(withSuccess());

        sender.send(subscription, event, attempt);
        mockServer.verify();
    }

    @Test
    void shouldIncludeCobreEventIdHeader() throws Exception {
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
    void shouldIncludeCobreAttemptHeader() throws Exception {
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

    @Test
    void shouldReturn200AsSuccess() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);

        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();

        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andRespond(withSuccess());

        var outcome = sender.send(subscription, event, attempt);

        assertThat(outcome).isInstanceOf(DeliveryOutcome.Success.class);
        var success = (DeliveryOutcome.Success) outcome;
        assertThat(success.responseStatus()).isEqualTo(200);
        assertThat(success.latency().toNanos()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldReturn503AsTransientFailure() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);

        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();

        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        var outcome = sender.send(subscription, event, attempt);

        assertThat(outcome).isInstanceOf(DeliveryOutcome.TransientFailure.class);
        var failure = (DeliveryOutcome.TransientFailure) outcome;
        assertThat(failure.responseStatus()).contains(503);
    }

    @Test
    void shouldReturn429AsTransientFailure() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);

        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();

        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));

        var outcome = sender.send(subscription, event, attempt);

        assertThat(outcome).isInstanceOf(DeliveryOutcome.TransientFailure.class);
    }

    @Test
    void shouldReturn408AsTransientFailure() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);

        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();

        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.REQUEST_TIMEOUT));

        var outcome = sender.send(subscription, event, attempt);

        assertThat(outcome).isInstanceOf(DeliveryOutcome.TransientFailure.class);
    }

    @Test
    void shouldReturn404AsPermanentFailure() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);

        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();

        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        var outcome = sender.send(subscription, event, attempt);

        assertThat(outcome).isInstanceOf(DeliveryOutcome.PermanentFailure.class);
    }

    @Test
    void shouldReturn400AsPermanentFailure() throws Exception {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var sender = createSender(restClient);

        var subscription = createSubscription("https://api.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();

        mockServer.expect(requestTo("https://api.example.com/hook"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        var outcome = sender.send(subscription, event, attempt);

        assertThat(outcome).isInstanceOf(DeliveryOutcome.PermanentFailure.class);
        var failure = (DeliveryOutcome.PermanentFailure) outcome;
        assertThat(failure.reason()).contains("client rejected");
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
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        );

        var signer = new WebhookSigner(FIXED_CLOCK);
        var mapper = new WebhookPayloadMapper(JsonMapper.builder().addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build());
        var sender = new HttpWebhookSender(restClient, signer, mapper, validator);

        var subscription = createSubscription("https://private.example.com/hook", "secret-key");
        var event = createEvent();
        var attempt = createAttempt();

        mockServer.expect(org.springframework.test.web.client.ExpectedCount.never(), requestTo(org.hamcrest.Matchers.any(String.class)))
            .andRespond(withSuccess());

        var outcome = sender.send(subscription, event, attempt);

        assertThat(outcome).isInstanceOf(DeliveryOutcome.PermanentFailure.class);
        var failure = (DeliveryOutcome.PermanentFailure) outcome;
        assertThat(failure.responseStatus()).isEqualTo(0);
        assertThat(failure.reason()).isEqualTo("invalid webhook url");
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
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        var signer = new WebhookSigner(FIXED_CLOCK);
        var mapper = new WebhookPayloadMapper(JsonMapper.builder().addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build());
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
