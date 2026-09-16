package co.cobre.notifications.infrastructure.worker;

import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.usecase.ListNotificationEventsQuery;
import co.cobre.notifications.application.usecase.NotificationEventPage;
import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.infrastructure.persistence.NotificationEventRepositoryAdapter;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Metered decorator for NotificationEventRepository that records delivery metrics.
 * Delegates to NotificationEventRepositoryAdapter and records delivery metrics
 * when events transition to terminal states (COMPLETED, FAILED, RETRYING).
 */
@Component
@org.springframework.context.annotation.Primary
public class MeteredNotificationEventRepository implements NotificationEventRepository {

    private final NotificationEventRepositoryAdapter delegate;
    private final DeliveryMetrics metrics;

    public MeteredNotificationEventRepository(NotificationEventRepositoryAdapter delegate, DeliveryMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public void save(NotificationEvent event) {
        delegate.save(event);
    }

    @Override
    public Optional<NotificationEvent> findByClientAndId(ClientId clientId, EventId eventId) {
        return delegate.findByClientAndId(clientId, eventId);
    }

    @Override
    public Optional<NotificationEvent> findById(EventId eventId) {
        return delegate.findById(eventId);
    }

    @Override
    public boolean existsById(EventId eventId) {
        return delegate.existsById(eventId);
    }

    @Override
    public NotificationEventPage search(ListNotificationEventsQuery query) {
        return delegate.search(query);
    }

    @Override
    public boolean transition(EventId eventId, DeliveryStatus expectedCurrent, NotificationEvent updated) {
        boolean result = delegate.transition(eventId, expectedCurrent, updated);

        if (result && shouldRecordMetric(updated.status())) {
            metrics.delivered(
                updated.clientId().value(),
                updated.status().name().toLowerCase(),
                updated.eventKey().value()
            );
        }

        return result;
    }

    private boolean shouldRecordMetric(DeliveryStatus status) {
        return status == DeliveryStatus.COMPLETED ||
               status == DeliveryStatus.FAILED ||
               status == DeliveryStatus.RETRYING;
    }
}
