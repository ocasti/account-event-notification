package co.cobre.notifications.infrastructure.worker;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountEventMessageMapperTest {

    private static final Instant OCCURRED_AT = Instant.parse("2025-01-01T10:00:00Z");

    private final AccountEventMessageMapper mapper = new AccountEventMessageMapper();

    @Test
    void shouldCopyEveryFieldIntoCommandWhenMessageIsValid() {
        var message = new AccountEventMessage("EVT001", "account.updated", "CLIENT123", "user updated", OCCURRED_AT);

        var command = mapper.toCommand(message);

        assertThat(command.eventId().value()).isEqualTo("EVT001");
        assertThat(command.clientId().value()).isEqualTo("CLIENT123");
        assertThat(command.eventKey().value()).isEqualTo("account.updated");
        assertThat(command.content()).isEqualTo("user updated");
        assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void shouldRejectMessageWhenEventTypeIsNotAValidEventKey() {
        var message = new AccountEventMessage("EVT002", "INVALID-TYPE", "CLIENT456", "content", OCCURRED_AT);

        assertThatThrownBy(() -> mapper.toCommand(message))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldProduceWildcardEventKeyWhenEventTypeIsAsterisk() {
        var message = new AccountEventMessage("EVT003", "*", "CLIENT789", "content", OCCURRED_AT);

        var command = mapper.toCommand(message);

        assertThat(command.eventKey().isWildcard()).isTrue();
    }
}
