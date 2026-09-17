package co.cobre.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountEventMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ReferenceEvent aReferenceEvent() {
        return new ReferenceEvent(
            "EVT001",
            "credit_card_payment",
            "CLIENT001",
            "Payment received",
            Instant.parse("2024-03-15T09:30:22Z")
        );
    }

    @Test
    void shouldMapAllFiveFieldsWhenBuildingAccountEventMessageFromReferenceEvent() {
        ReferenceEvent event = aReferenceEvent();

        AccountEventMessage message = AccountEventMessage.from(event);

        assertThat(message.eventId()).isEqualTo("EVT001");
        assertThat(message.eventType()).isEqualTo("credit_card_payment");
        assertThat(message.clientId()).isEqualTo("CLIENT001");
        assertThat(message.content()).isEqualTo("Payment received");
        assertThat(message.occurredAt()).isEqualTo("2024-03-15T09:30:22Z");
    }

    @Test
    void shouldSerializeAccountEventMessageFieldsInSnakeCaseWhenWritingJson() throws Exception {
        AccountEventMessage message = AccountEventMessage.from(aReferenceEvent());

        String json = mapper.writeValueAsString(message);
        Map<String, Object> parsed = mapper.readValue(json, Map.class);

        assertThat(parsed).containsKeys("event_id", "event_type", "client_id", "content", "occurred_at");
        assertThat(parsed).doesNotContainKeys("eventId", "eventType", "clientId", "occurredAt");
    }

    @Test
    void shouldSerializeAccountEventMessageOccurredAtAsIso8601UtcWhenWritingJson() throws Exception {
        AccountEventMessage message = AccountEventMessage.from(aReferenceEvent());

        String json = mapper.writeValueAsString(message);
        Map<String, Object> parsed = mapper.readValue(json, Map.class);

        assertThat(parsed.get("occurred_at")).isEqualTo("2024-03-15T09:30:22Z");
    }
}
