package co.cobre.notifications.infrastructure.persistence.adapter;

import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.query.ListNotificationEventsQuery;
import co.cobre.notifications.application.query.NotificationEventPage;
import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.infrastructure.persistence.jpa.NotificationEventJpaRepository;
import co.cobre.notifications.infrastructure.persistence.mapper.NotificationEventEntityMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository adapter for notification events.
 */
@Repository
public class NotificationEventRepositoryAdapter implements NotificationEventRepository {

    private final NotificationEventJpaRepository jpaRepository;
    private final NotificationEventEntityMapper mapper;

    /**
     * Creates a new notification event repository adapter.
     */
    public NotificationEventRepositoryAdapter(
        NotificationEventJpaRepository jpaRepository,
        NotificationEventEntityMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(NotificationEvent event) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<NotificationEvent> findByClientAndId(ClientId clientId, EventId eventId) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<NotificationEvent> findById(EventId eventId) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean existsById(EventId eventId) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public NotificationEventPage search(ListNotificationEventsQuery query) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean transition(EventId eventId, DeliveryStatus expectedCurrent, NotificationEvent updated) {
        throw new UnsupportedOperationException("not implemented");
    }
}
