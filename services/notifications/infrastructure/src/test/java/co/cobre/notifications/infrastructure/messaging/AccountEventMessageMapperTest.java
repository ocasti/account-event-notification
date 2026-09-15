package co.cobre.notifications.infrastructure.messaging;

import co.cobre.notifications.application.command.RegisterEventCommand;
import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.domain.model.EventKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AccountEventMessageMapperTest {

    private final AccountEventMessageMapper mapper = new AccountEventMessageMapper();

    @Test
    void mapsValidMessageToCommand() {
        var message = new AccountEventMessage(
            "EVT001",
            "account.updated",
            "CLIENT123",
            "user updated",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        var command = mapper.toCommand(message);

        assertEquals("EVT001", command.eventId().value());
        assertEquals("CLIENT123", command.clientId().value());
        assertEquals("account.updated", command.eventKey().value());
        assertEquals("user updated", command.content());
        assertEquals(Instant.parse("2025-01-01T10:00:00Z"), command.occurredAt());
    }

    @Test
    void throwsOnInvalidEventType() {
        var message = new AccountEventMessage(
            "EVT002",
            "INVALID-TYPE",
            "CLIENT456",
            "content",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        assertThrows(IllegalArgumentException.class, () -> mapper.toCommand(message));
    }

    @Test
    void mapsWildcardEventType() {
        var message = new AccountEventMessage(
            "EVT003",
            "*",
            "CLIENT789",
            "content",
            Instant.parse("2025-01-01T10:00:00Z")
        );

        var command = mapper.toCommand(message);

        assertTrue(command.eventKey().isWildcard());
    }
}
