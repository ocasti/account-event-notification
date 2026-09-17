package co.cobre.notifications.domain.fixtures;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventData;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.NotificationEvent;

import java.time.Instant;
import java.util.Optional;

/**
 * Object Mother for {@link NotificationEvent}. {@link #pending()}/{@link #pendingFor(ClientId)}
 * and {@link #skipped()} go through the real {@link NotificationEvent#register} and
 * {@link NotificationEvent#skipped(EventData, Instant)} factories; the other status variants
 * and {@link #aPendingEvent()} use the event's own reconstitution constructor with the same
 * field combinations those factories produce (a subscription id present unless skipped, a
 * delivered-at timestamp only when completed) so arbitrary status/cycle combinations
 * (e.g. a failed event mid-replay-cycle) can be built directly.
 */
public final class NotificationEvents {

    private static final String CONTENT = "Credit card payment received for $150.00";
    private static final String SUBSCRIPTION_ID = "sub-001";

    private NotificationEvents() {
    }

    public static NotificationEvent pending() {
        return pendingFor(Ids.CLIENT_001);
    }

    public static NotificationEvent pendingFor(ClientId clientId) {
        var data = new EventData(Ids.EVT_001, clientId, Ids.CREDIT_CARD_PAYMENT, CONTENT, Clocks.NOW);
        var subscription = Subscriptions.aSubscription().withId(SUBSCRIPTION_ID).withClientId(clientId).build();
        return NotificationEvent.register(data, Clocks.NOW, subscription);
    }

    public static NotificationEvent retrying() {
        return aPendingEvent().withStatus(DeliveryStatus.RETRYING).build();
    }

    public static NotificationEvent completed() {
        return aPendingEvent().withStatus(DeliveryStatus.COMPLETED).build();
    }

    public static NotificationEvent failed() {
        return aPendingEvent().withStatus(DeliveryStatus.FAILED).build();
    }

    public static NotificationEvent skipped() {
        var data = new EventData(Ids.EVT_001, Ids.CLIENT_001, Ids.CREDIT_CARD_PAYMENT, CONTENT, Clocks.NOW);
        return NotificationEvent.skipped(data, Clocks.NOW);
    }

    public static Builder aPendingEvent() {
        return new Builder();
    }

    public static final class Builder {
        private EventId eventId = Ids.EVT_001;
        private ClientId clientId = Ids.CLIENT_001;
        private EventKey eventKey = Ids.CREDIT_CARD_PAYMENT;
        private String content = CONTENT;
        private Instant createdAt = Clocks.NOW;
        private DeliveryStatus status = DeliveryStatus.PENDING;
        private int cycle;
        private Optional<String> subscriptionId = Optional.of(SUBSCRIPTION_ID);

        private Builder() {
        }

        public Builder withEventId(EventId eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder withClientId(ClientId clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder withEventKey(EventKey eventKey) {
            this.eventKey = eventKey;
            return this;
        }

        public Builder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder withStatus(DeliveryStatus status) {
            this.status = status;
            return this;
        }

        public Builder withContent(String content) {
            this.content = content;
            return this;
        }

        public Builder withoutSubscription() {
            this.subscriptionId = Optional.empty();
            return this;
        }

        public Builder withCycle(int cycle) {
            this.cycle = cycle;
            return this;
        }

        public Builder withSubscriptionId(String subscriptionId) {
            this.subscriptionId = Optional.of(subscriptionId);
            return this;
        }

        public NotificationEvent build() {
            return new NotificationEvent(
                eventId,
                clientId,
                eventKey,
                content,
                createdAt,
                Clocks.NOW,
                status,
                subscriptionId,
                cycle,
                deliveredAt()
            );
        }

        private Optional<Instant> deliveredAt() {
            return status == DeliveryStatus.COMPLETED ? Optional.of(Clocks.NOW) : Optional.empty();
        }
    }
}
