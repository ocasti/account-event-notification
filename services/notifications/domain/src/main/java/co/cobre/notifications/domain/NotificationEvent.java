package co.cobre.notifications.domain;

import co.cobre.notifications.domain.IllegalStateTransitionException;
import co.cobre.notifications.domain.ReplayNotAllowedException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable aggregate root representing a notification event.
 */
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

    /**
     * Creates a new notification event with all fields.
     */
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

    /**
     * Factory method to register a new notification event.
     */
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

    /**
     * Factory method to create a skipped notification event.
     */
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

    /**
     * Marks the event as completed at the given time.
     */
    public void complete(Instant at) {
        if (!status.canTransitionTo(DeliveryStatus.COMPLETED)) {
            throw new IllegalStateTransitionException(status, DeliveryStatus.COMPLETED);
        }
        status = DeliveryStatus.COMPLETED;
        deliveredAt = Optional.of(at);
    }

    /**
     * Schedules a retry of the event.
     */
    public void scheduleRetry() {
        if (!status.canTransitionTo(DeliveryStatus.RETRYING)) {
            throw new IllegalStateTransitionException(status, DeliveryStatus.RETRYING);
        }
        status = DeliveryStatus.RETRYING;
    }

    /**
     * Marks the event as failed.
     */
    public void fail() {
        if (!status.canTransitionTo(DeliveryStatus.FAILED)) {
            throw new IllegalStateTransitionException(status, DeliveryStatus.FAILED);
        }
        status = DeliveryStatus.FAILED;
    }

    /**
     * Marks the event for replay.
     */
    public void replay() {
        if (status != DeliveryStatus.FAILED) {
            throw new ReplayNotAllowedException(eventId, status);
        }
        status = DeliveryStatus.PENDING;
        cycle++;
        deliveredAt = Optional.empty();
    }

    /**
     * Returns the event ID.
     */
    public EventId eventId() {
        return eventId;
    }

    /**
     * Returns the client ID.
     */
    public ClientId clientId() {
        return clientId;
    }

    /**
     * Returns the event key.
     */
    public EventKey eventKey() {
        return eventKey;
    }

    /**
     * Returns the content.
     */
    public String content() {
        return content;
    }

    /**
     * Returns the creation timestamp.
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Returns the reception timestamp.
     */
    public Instant receivedAt() {
        return receivedAt;
    }

    /**
     * Returns the delivery status.
     */
    public DeliveryStatus status() {
        return status;
    }

    /**
     * Returns the subscription ID if available.
     */
    public Optional<String> subscriptionId() {
        return subscriptionId;
    }

    /**
     * Returns the delivery cycle.
     */
    public int cycle() {
        return cycle;
    }

    /**
     * Returns the delivery timestamp if available.
     */
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
