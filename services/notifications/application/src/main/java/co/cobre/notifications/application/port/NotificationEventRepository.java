package co.cobre.notifications.application.port;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.NotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEventsQuery;
import co.cobre.notifications.application.usecase.NotificationEventPage;

import java.util.Optional;

public interface NotificationEventRepository {

    void save(NotificationEvent event);

    Optional<NotificationEvent> findByClientAndId(ClientId clientId, EventId eventId);

    Optional<NotificationEvent> findById(EventId eventId);

    boolean existsById(EventId eventId);

    NotificationEventPage search(ListNotificationEventsQuery query);

    boolean transition(EventId eventId, DeliveryStatus expectedCurrent, NotificationEvent updated);
}
