package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.domain.EventId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class CursorCodec {

    public String encode(Instant createdAt, EventId eventId) {
        throw new UnsupportedOperationException("not implemented");
    }

    public Optional<Cursor> decode(String raw) {
        throw new UnsupportedOperationException("not implemented");
    }

    public record Cursor(Instant createdAt, EventId eventId) {}
}
