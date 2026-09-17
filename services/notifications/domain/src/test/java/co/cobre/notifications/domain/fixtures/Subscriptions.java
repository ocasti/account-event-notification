package co.cobre.notifications.domain.fixtures;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.domain.WebhookUrl;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Object Mother for {@link Subscription}. All variants are active and cover
 * {@link Ids#CREDIT_CARD_PAYMENT} for {@link Ids#CLIENT_001} unless overridden through
 * {@link #aSubscription()}.
 */
public final class Subscriptions {

    private static final String SUBSCRIPTION_ID = "sub-001";
    private static final String SIGNATURE_KEY = "whsec_test_signature_key";

    private Subscriptions() {
    }

    public static Subscription active() {
        return aSubscription().build();
    }

    public static Subscription activeFor(ClientId clientId) {
        return aSubscription().withClientId(clientId).build();
    }

    public static Subscription signed() {
        return aSubscription().withSignatureKey(SIGNATURE_KEY).build();
    }

    public static Subscription unsigned() {
        return aSubscription().build();
    }

    public static Subscription inactive() {
        return aSubscription().withActive(false).build();
    }

    public static Builder aSubscription() {
        return new Builder();
    }

    public static final class Builder {
        private String id = SUBSCRIPTION_ID;
        private ClientId clientId = Ids.CLIENT_001;
        private Set<EventKey> eventKeys = Set.of(Ids.CREDIT_CARD_PAYMENT);
        private WebhookUrl url = WebhookUrl.of("https://example.com/webhook");
        private Optional<String> description = Optional.empty();
        private Optional<String> signatureKey = Optional.empty();
        private boolean active = true;
        private Instant createdAt = Clocks.NOW;

        private Builder() {
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withClientId(ClientId clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder withEventKeys(Set<EventKey> eventKeys) {
            this.eventKeys = eventKeys;
            return this;
        }

        public Builder withUrl(WebhookUrl url) {
            this.url = url;
            return this;
        }

        public Builder withDescription(String description) {
            this.description = Optional.of(description);
            return this;
        }

        public Builder withSignatureKey(String signatureKey) {
            this.signatureKey = Optional.of(signatureKey);
            return this;
        }

        public Builder withActive(boolean active) {
            this.active = active;
            return this;
        }

        public Builder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Subscription build() {
            return new Subscription(id, clientId, eventKeys, url, description, signatureKey, active, createdAt);
        }
    }
}
