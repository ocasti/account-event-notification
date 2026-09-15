package co.cobre.notifications.infrastructure.persistence.adapter;

import co.cobre.notifications.application.port.out.NotificationEventRepository;
import co.cobre.notifications.application.query.ListNotificationEventsQuery;
import co.cobre.notifications.application.query.NotificationEventPage;
import co.cobre.notifications.domain.model.ClientId;
import co.cobre.notifications.domain.model.DeliveryStatus;
import co.cobre.notifications.domain.model.EventId;
import co.cobre.notifications.domain.model.NotificationEvent;
import co.cobre.notifications.infrastructure.persistence.entity.DeliveryStatusEntity;
import co.cobre.notifications.infrastructure.persistence.jpa.NotificationEventJpaRepository;
import co.cobre.notifications.infrastructure.persistence.jpa.SearchCriteria;
import co.cobre.notifications.infrastructure.persistence.mapper.NotificationEventEntityMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class NotificationEventRepositoryAdapter implements NotificationEventRepository {

    private final NotificationEventJpaRepository jpaRepository;
    private final NotificationEventEntityMapper mapper;

    public NotificationEventRepositoryAdapter(
        NotificationEventJpaRepository jpaRepository,
        NotificationEventEntityMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(NotificationEvent event) {
        var entity = mapper.toEntity(event);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<NotificationEvent> findByClientAndId(ClientId clientId, EventId eventId) {
        return jpaRepository.findByEventIdAndClientId(eventId.value(), clientId.value())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<NotificationEvent> findById(EventId eventId) {
        return jpaRepository.findById(eventId.value())
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsById(EventId eventId) {
        return jpaRepository.existsById(eventId.value());
    }

    @Override
    public NotificationEventPage search(ListNotificationEventsQuery query) {
        var cursorInfo = query.cursor()
            .map(CursorCodec::decode);

        var searchCriteria = new SearchCriteria(
            query.clientId().value(),
            query.status().map(this::mapStatusToEntity),
            query.from(),
            query.to(),
            cursorInfo.map(CursorCodec.Cursor::createdAt),
            cursorInfo.map(CursorCodec.Cursor::eventId),
            query.limit()
        );

        var results = jpaRepository.search(searchCriteria).getContent();

        Optional<String> nextCursor = Optional.empty();
        if (results.size() > query.limit()) {
            var lastItem = results.get(query.limit() - 1);
            nextCursor = Optional.of(CursorCodec.encode(lastItem.getCreatedAt(), lastItem.getEventId()));
            results = results.subList(0, query.limit());
        }

        return new NotificationEventPage(
            results.stream().map(mapper::toDomain).toList(),
            nextCursor
        );
    }

    @Override
    @Transactional
    public boolean transition(EventId eventId, DeliveryStatus expectedCurrent, NotificationEvent updated) {
        int rows = jpaRepository.updateStatusIfMatches(
            eventId.value(),
            mapStatusToEntity(expectedCurrent),
            mapStatusToEntity(updated.status()),
            updated.cycle(),
            updated.deliveredAt().orElse(null)
        );
        return rows == 1;
    }

    private DeliveryStatusEntity mapStatusToEntity(DeliveryStatus status) {
        return DeliveryStatusEntity.valueOf(status.name());
    }
}
