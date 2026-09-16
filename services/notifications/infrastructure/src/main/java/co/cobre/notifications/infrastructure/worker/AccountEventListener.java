package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class AccountEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AccountEventListener.class);

    private final RegisterNotificationEvent registerNotificationEvent;
    private final AccountEventMessageMapper mapper;
    private final DeliveryMetrics metrics;

    public AccountEventListener(
        RegisterNotificationEvent registerNotificationEvent,
        AccountEventMessageMapper mapper,
        DeliveryMetrics metrics
    ) {
        this.registerNotificationEvent = registerNotificationEvent;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    /**
     * Listens for account events from SQS.
     */
    @SqsListener("${notifications.sqs.queue-name}")
    public void onMessage(AccountEventMessage message) {
        var command = mapper.toCommand(message);
        var result = registerNotificationEvent.register(command);

        switch (result) {
            case REGISTERED -> metrics.registered(command.clientId().value(), command.eventKey().value());
            case SKIPPED -> metrics.skipped(command.clientId().value(), command.eventKey().value());
            case DUPLICATE -> {
                metrics.duplicate(command.clientId().value(), command.eventKey().value());
                logger.atDebug()
                    .addKeyValue("event_id", command.eventId().value())
                    .addKeyValue("client_id", command.clientId().value())
                    .log("duplicate account event ignored");
            }
        }
    }
}
