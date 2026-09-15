package co.cobre.notifications.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable aggregate root representing a notification event.
 */
public final class NotificationEvent {
    private final String eventId;
    private final String clientId;
    private final String eventKey;
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
        String eventId,
        String clientId,
        String eventKey,
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
        String eventId,
        String clientId,
        String eventKey,
        String content,
        Instant createdAt,
        Instant receivedAt,
        Subscription subscription
    ) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Factory method to create a skipped notification event.
     */
    public static NotificationEvent skipped(
        String eventId,
        String clientId,
        String eventKey,
        String content,
        Instant createdAt,
        Instant receivedAt
    ) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Marks the event as completed at the given time.
     */
    public void complete(Instant at) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Schedules a retry of the event.
     */
    public void scheduleRetry() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Marks the event as failed.
     */
    public void fail() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Marks the event for replay.
     */
    public void replay() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the event ID.
     */
    public String eventId() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the client ID.
     */
    public String clientId() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the event key.
     */
    public String eventKey() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the content.
     */
    public String content() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the creation timestamp.
     */
    public Instant createdAt() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the reception timestamp.
     */
    public Instant receivedAt() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the delivery status.
     */
    public DeliveryStatus status() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the subscription ID if available.
     */
    public Optional<String> subscriptionId() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the delivery cycle.
     */
    public int cycle() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the delivery timestamp if available.
     */
    public Optional<Instant> deliveredAt() {
        throw new UnsupportedOperationException("not implemented");
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
