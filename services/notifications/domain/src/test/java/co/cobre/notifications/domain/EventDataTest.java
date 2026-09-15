package co.cobre.notifications.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EventDataTest {
    private static final EventId EVENT_ID = new EventId("test-event-id");
    private static final ClientId CLIENT_ID = new ClientId("test-client");
    private static final EventKey EVENT_KEY = new EventKey("test.event.key");
    private static final String CONTENT = "test content";
    private static final Instant OCCURRED_AT = Instant.now();

    @Test
    void rejectsNullEventId() {
        assertThrows(NullPointerException.class, () ->
            new EventData(null, CLIENT_ID, EVENT_KEY, CONTENT, OCCURRED_AT)
        );
    }

    @Test
    void rejectsNullClientId() {
        assertThrows(NullPointerException.class, () ->
            new EventData(EVENT_ID, null, EVENT_KEY, CONTENT, OCCURRED_AT)
        );
    }

    @Test
    void rejectsNullEventKey() {
        assertThrows(NullPointerException.class, () ->
            new EventData(EVENT_ID, CLIENT_ID, null, CONTENT, OCCURRED_AT)
        );
    }

    @Test
    void rejectsNullContent() {
        assertThrows(NullPointerException.class, () ->
            new EventData(EVENT_ID, CLIENT_ID, EVENT_KEY, null, OCCURRED_AT)
        );
    }

    @Test
    void rejectsNullOccurredAt() {
        assertThrows(NullPointerException.class, () ->
            new EventData(EVENT_ID, CLIENT_ID, EVENT_KEY, CONTENT, null)
        );
    }
}
