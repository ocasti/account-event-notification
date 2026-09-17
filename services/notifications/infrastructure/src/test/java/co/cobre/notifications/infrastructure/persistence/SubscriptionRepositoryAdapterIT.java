package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionRepositoryAdapterIT extends PersistenceTestSupport {

    @Autowired
    private SubscriptionRepositoryAdapter adapter;

    @Autowired
    private SubscriptionJpaRepository jpaRepository;

    @Test
    void shouldReturnMatchingActiveSubscriptionWhenClientHasMultipleCandidates() {
        createAndSave("sub_client001", "CLIENT001", new String[]{"credit_deposit", "payment_received"}, true);
        createAndSave("sub_client001_alt", "CLIENT001", new String[]{"*"}, true);
        createAndSave("sub_other", "OTHER_CLIENT", new String[]{"credit_deposit"}, true);

        var result = adapter.findActive(new ClientId("CLIENT001"), new EventKey("credit_deposit"));

        assertThat(result).isPresent();
        assertThat(result.get().id()).isIn("sub_client001", "sub_client001_alt");
    }

    @Test
    void shouldReturnWildcardSubscriptionWhenNoExactEventKeyMatchExists() {
        createAndSave("sub_wildcard", "CLIENT_T1", new String[]{"*"}, true);

        var result = adapter.findActive(new ClientId("CLIENT_T1"), new EventKey("any_event_key"));

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("sub_wildcard");
    }

    @Test
    void shouldReturnEmptyWhenClientHasNoSubscriptions() {
        createAndSave("sub_other", "OTHER", new String[]{"event"}, true);

        var result = adapter.findActive(new ClientId("NONEXISTENT"), new EventKey("event"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldIgnoreInactiveSubscriptionsWhenFindingActive() {
        createAndSave("sub_inactive", "CLIENT_T1", new String[]{"credit_deposit"}, false);
        createAndSave("sub_active", "CLIENT_T1", new String[]{"payment_received"}, true);

        var result = adapter.findActive(new ClientId("CLIENT_T1"), new EventKey("credit_deposit"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnSubscriptionWhenFindingById() {
        createAndSave("sub_123", "CLIENT001", new String[]{"event.test"}, true);

        var result = adapter.findById("sub_123");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("sub_123");
        assertThat(result.get().clientId().value()).isEqualTo("CLIENT001");
    }

    @Test
    void shouldReturnEmptyWhenFindingByIdForNonexistentSubscription() {
        var result = adapter.findById("nonexistent_sub");

        assertThat(result).isEmpty();
    }

    private SubscriptionEntity createAndSave(String id, String clientId, String[] eventKeys, boolean active) {
        var entity = new SubscriptionEntity();
        entity.setId(id);
        entity.setClientId(clientId);
        entity.setEventKeys(eventKeys);
        entity.setUrl("https://example.test/webhook");
        entity.setDescription("Test subscription");
        entity.setEventSignatureKey("sig-key");
        entity.setActive(active);
        entity.setCreatedAt(Instant.now());
        return jpaRepository.save(entity);
    }
}
