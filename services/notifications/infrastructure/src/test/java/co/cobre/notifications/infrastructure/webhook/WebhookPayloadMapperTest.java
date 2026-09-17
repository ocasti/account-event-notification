package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
        var event = new NotificationEvent(
            new EventId("event-123"),
            new ClientId("client-456"),
            new EventKey("user.created"),
            "{\"name\":\"John\"}",
            Instant.parse("2025-09-15T10:00:00Z"),
            Instant.parse("2025-09-15T10:00:01Z"),
            co.cobre.notifications.domain.DeliveryStatus.PENDING,
            Optional.of("sub-789"),
            0,
            Optional.empty()
        );

        var json = mapper.toJson(event);

        assertThat(json).contains(
            "\"id\":\"event-123\"",
            "\"event_key\":\"user.created\"",
            "\"client_id\":\"client-456\"",
            "\"created_at\":\"2025-09-15T10:00:00Z\"",
            "\"content\":\"{\\\"name\\\":\\\"John\\\"}\""
        );
    }

    @Test
    void shouldProduceCompactJsonWhenMappingEventToJson() {
        var event = new NotificationEvent(
            new EventId("event-1"),
            new ClientId("client-2"),
            new EventKey("test"),
            "content",
            Instant.parse("2025-09-15T10:00:00Z"),
            Instant.parse("2025-09-15T10:00:01Z"),
            co.cobre.notifications.domain.DeliveryStatus.PENDING,
            Optional.empty(),
            0,
            Optional.empty()
        );

        var json = mapper.toJson(event);

        assertThat(json).doesNotContain(" ", "\n", "\r");
    }

    @Test
    void shouldMaintainFieldOrderWhenMappingEventToJson() {
        var event = new NotificationEvent(
            new EventId("event-1"),
            new ClientId("client-2"),
            new EventKey("test"),
            "content",
            Instant.parse("2025-09-15T10:00:00Z"),
            Instant.parse("2025-09-15T10:00:01Z"),
            co.cobre.notifications.domain.DeliveryStatus.PENDING,
            Optional.empty(),
            0,
            Optional.empty()
        );

        var json = mapper.toJson(event);
        var idIdx = json.indexOf("\"id\"");
        var eventKeyIdx = json.indexOf("\"event_key\"");
        var clientIdIdx = json.indexOf("\"client_id\"");
        var createdAtIdx = json.indexOf("\"created_at\"");
        var contentIdx = json.indexOf("\"content\"");
        var fieldOrder = List.of(idIdx, eventKeyIdx, clientIdIdx, createdAtIdx, contentIdx);

        assertThat(fieldOrder).isSorted();
    }
}
