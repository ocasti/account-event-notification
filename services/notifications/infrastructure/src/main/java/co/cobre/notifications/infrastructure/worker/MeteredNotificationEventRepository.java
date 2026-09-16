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
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<NotificationEvent> findByClientAndId(ClientId clientId, EventId eventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<NotificationEvent> findById(EventId eventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean existsById(EventId eventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NotificationEventPage search(ListNotificationEventsQuery query) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean transition(EventId eventId, DeliveryStatus expectedCurrent, NotificationEvent updated) {
        throw new UnsupportedOperationException();
    }
}
