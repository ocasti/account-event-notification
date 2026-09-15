package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.RegisterEventCommand;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import org.springframework.stereotype.Component;

/**
 * Mapper for account event messages.
 */
@Component
public class AccountEventMessageMapper {

    /**
     * Maps account event message to register event command.
     */
    public RegisterEventCommand toCommand(AccountEventMessage message) {
        return new RegisterEventCommand(
            new EventId(message.eventId()),
            new ClientId(message.clientId()),
            new EventKey(message.eventType()),
            message.content(),
            message.occurredAt()
        );
    }
}
