package co.cobre.notifications.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class NotificationEvent {
    private final EventId eventId;
    private final ClientId clientId;
    private final EventKey eventKey;
    private final String content;
    private final Instant createdAt;
    private final Instant receivedAt;
    private DeliveryStatus status;
    private Optional<String> subscriptionId;
    private int cycle;
    private Optional<Instant> deliveredAt;

    public NotificationEvent(
        EventId eventId,
        ClientId clientId,
        EventKey eventKey,
        String content,
        Instant createdAt,
        Instant receivedAt,
        DeliveryStatus status,
        Optional<String> subscriptionId,
        int cycle,
        Optional<Instant> deliveredAt
    ) {
        this.eventId = eventId;
        this.clientId = clientId;
        this.eventKey = eventKey;
        this.content = content;
        this.createdAt = createdAt;
        this.receivedAt = receivedAt;
        this.status = status;
        this.subscriptionId = subscriptionId;
        this.cycle = cycle;
        this.deliveredAt = deliveredAt;
    }

    public static NotificationEvent register(
        EventData data,
        Instant receivedAt,
        Subscription subscription
    ) {
        return new NotificationEvent(
            data.eventId(),
            data.clientId(),
            data.eventKey(),
            data.content(),
            data.occurredAt(),
            receivedAt,
            DeliveryStatus.PENDING,
            Optional.of(subscription.id()),
            0,
            Optional.empty()
        );
    }

    public static NotificationEvent skipped(
        EventData data,
        Instant receivedAt
    ) {
        return new NotificationEvent(
            data.eventId(),
            data.clientId(),
            data.eventKey(),
            data.content(),
            data.occurredAt(),
            receivedAt,
            DeliveryStatus.SKIPPED,
            Optional.empty(),
            0,
            Optional.empty()
        );
    }

    public void complete(Instant at) {
        if (!status.canTransitionTo(DeliveryStatus.COMPLETED)) {
            throw new IllegalStateTransitionException(status, DeliveryStatus.COMPLETED);
        }
        status = DeliveryStatus.COMPLETED;
        deliveredAt = Optional.of(at);
    }

    public void scheduleRetry() {
        if (!status.canTransitionTo(DeliveryStatus.RETRYING)) {
            throw new IllegalStateTransitionException(status, DeliveryStatus.RETRYING);
        }
        status = DeliveryStatus.RETRYING;
    }

    public void fail() {
        if (!status.canTransitionTo(DeliveryStatus.FAILED)) {
            throw new IllegalStateTransitionException(status, DeliveryStatus.FAILED);
        }
        status = DeliveryStatus.FAILED;
    }

    public void replay() {
        if (status != DeliveryStatus.FAILED) {
            throw new ReplayNotAllowedException(eventId, status);
        }
        status = DeliveryStatus.PENDING;
        cycle++;
        deliveredAt = Optional.empty();
    }

    public EventId eventId() {
        return eventId;
    }

    public ClientId clientId() {
        return clientId;
    }

    public EventKey eventKey() {
        return eventKey;
    }

    public String content() {
        return content;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public DeliveryStatus status() {
        return status;
    }

    public Optional<String> subscriptionId() {
        return subscriptionId;
    }

    public int cycle() {
        return cycle;
    }

    public Optional<Instant> deliveredAt() {
        return deliveredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationEvent)) return false;
        NotificationEvent that = (NotificationEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}
