package co.cobre.notifications.infrastructure.webhook;

import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.domain.model.EventKey;
import co.cobre.notifications.domain.model.NotificationEvent;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookPayloadMapperTest {

    private WebhookPayloadMapper mapper;

    @BeforeEach
    void setUp() {
        var jsonMapperWithoutModule = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        mapper = new WebhookPayloadMapper(jsonMapperWithoutModule);
    }

    @Test
    void shouldMapEventToJsonWithAllRequiredFields() {
        var event = new NotificationEvent(
            new EventId("event-123"),
            new ClientId("client-456"),
            new EventKey("user.created"),
            "{\"name\":\"John\"}",
            Instant.parse("2025-09-15T10:00:00Z"),
            Instant.parse("2025-09-15T10:00:01Z"),
            co.cobre.notifications.domain.model.DeliveryStatus.PENDING,
            Optional.of("sub-789"),
            0,
            Optional.empty()
        );

        var json = mapper.toJson(event);

        assertThat(json).contains("\"id\":\"event-123\"");
        assertThat(json).contains("\"event_key\":\"user.created\"");
        assertThat(json).contains("\"client_id\":\"client-456\"");
        assertThat(json).contains("\"created_at\":\"2025-09-15T10:00:00Z\"");
        assertThat(json).contains("\"content\":\"{\\\"name\\\":\\\"John\\\"}\"");
    }

    @Test
    void shouldProduceCompactJsonWithoutSpacesOrNewlines() {
        var event = new NotificationEvent(
            new EventId("event-1"),
            new ClientId("client-2"),
            new EventKey("test"),
            "content",
            Instant.parse("2025-09-15T10:00:00Z"),
            Instant.parse("2025-09-15T10:00:01Z"),
            co.cobre.notifications.domain.model.DeliveryStatus.PENDING,
            Optional.empty(),
            0,
            Optional.empty()
        );

        var json = mapper.toJson(event);

        assertThat(json).doesNotContain(" ");
        assertThat(json).doesNotContain("\n");
        assertThat(json).doesNotContain("\r");
    }

    @Test
    void shouldMaintainOrderOfFields() {
        var event = new NotificationEvent(
            new EventId("event-1"),
            new ClientId("client-2"),
            new EventKey("test"),
            "content",
            Instant.parse("2025-09-15T10:00:00Z"),
            Instant.parse("2025-09-15T10:00:01Z"),
            co.cobre.notifications.domain.model.DeliveryStatus.PENDING,
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

        assertThat(idIdx).isLessThan(eventKeyIdx);
        assertThat(eventKeyIdx).isLessThan(clientIdIdx);
        assertThat(clientIdIdx).isLessThan(createdAtIdx);
        assertThat(createdAtIdx).isLessThan(contentIdx);
    }
}
