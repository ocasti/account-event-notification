package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookPayloadMapperTest {

    private WebhookPayloadMapper mapper;

    @BeforeEach
    void setUp() {
        var jsonMapper = JsonMapper.builder().build();
        mapper = new WebhookPayloadMapper(jsonMapper);
    }

    @Test
    void shouldIncludeRequiredFieldsWhenMappingEventToJson() {
        var event = NotificationEvents.aPendingEvent()
            .withEventId(new EventId("event-123"))
            .withClientId(new ClientId("client-456"))
            .withEventKey(new EventKey("user.created"))
            .withContent("{\"name\":\"John\"}")
            .withCreatedAt(Instant.parse("2025-09-15T10:00:00Z"))
            .withSubscriptionId("sub-789")
            .build();

        var json = mapper.toJson(event);

        assertThat(json).contains(
            "\"id\":\"event-123\"",
            "\"event_key\":\"user.created\"",
            "\"client_id\":\"client-456\"",
            "\"created_at\":\"2025-09-15T10:00:00Z\"",
            "\"content\":\"{\\\"name\\\":\\\"John\\\"}\""
        );
    }

    /**
     * "Compact" means no pretty-printing (no newlines, no space after {@code :} or {@code ,}),
     * not that no field value may contain a space — {@code NotificationEvents.pending()}'s
     * content is "Credit card payment received for $150.00".
     */
    @Test
    void shouldProduceCompactJsonWhenMappingEventToJson() {
        var json = mapper.toJson(NotificationEvents.pending());

        assertThat(json).doesNotContain("\n", "\r", ": ", ", ");
    }

    @Test
    void shouldMaintainFieldOrderWhenMappingEventToJson() {
        var json = mapper.toJson(NotificationEvents.pending());
        var idIdx = json.indexOf("\"id\"");
        var eventKeyIdx = json.indexOf("\"event_key\"");
        var clientIdIdx = json.indexOf("\"client_id\"");
        var createdAtIdx = json.indexOf("\"created_at\"");
        var contentIdx = json.indexOf("\"content\"");
        var fieldOrder = List.of(idIdx, eventKeyIdx, clientIdIdx, createdAtIdx, contentIdx);

        assertThat(fieldOrder).isSorted();
    }
}
