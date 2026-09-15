package co.cobre.notifications.infrastructure.messaging;

import co.cobre.notifications.application.command.RegisterEventCommand;
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
        throw new UnsupportedOperationException("not implemented");
    }
}
