package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.domain.EventId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Codec for encoding and decoding pagination cursors.
 */
@Component
public class CursorCodec {

    /**
     * Encodes a cursor from creation timestamp and event ID.
     */
    public String encode(Instant createdAt, EventId eventId) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Decodes a cursor from its raw string representation.
     */
    public Optional<Cursor> decode(String raw) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Represents a decoded pagination cursor.
     */
    public record Cursor(Instant createdAt, EventId eventId) {}
}
