package co.cobre.notifications.infrastructure.messaging;

import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import co.cobre.notifications.infrastructure.metrics.DeliveryMetrics;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * SQS listener for account events.
 */
@Component
@Profile("worker")
public class AccountEventListener {

    private final RegisterNotificationEvent registerNotificationEvent;
    private final AccountEventMessageMapper mapper;
    private final DeliveryMetrics metrics;

    /**
     * Creates a new account event listener.
     */
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
        throw new UnsupportedOperationException("not implemented");
    }
}
