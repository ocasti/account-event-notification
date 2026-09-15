package co.cobre.notifications.infrastructure.persistence;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.Subscription;
import co.cobre.notifications.domain.WebhookUrl;
import co.cobre.notifications.infrastructure.persistence.PersistenceTestSupport;
import co.cobre.notifications.infrastructure.persistence.SubscriptionEntity;
import co.cobre.notifications.infrastructure.persistence.SubscriptionJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionRepositoryAdapterIT extends PersistenceTestSupport {

    @Autowired
    private SubscriptionRepositoryAdapter adapter;

    @Autowired
    private SubscriptionJpaRepository jpaRepository;

    @Test
    void testFindActiveReturnsSubscriptionsForClient() {
        var sub1 = createAndSave("sub_client001", "CLIENT001", new String[]{"credit_deposit", "payment_received"}, true);
        var sub2 = createAndSave("sub_client001_alt", "CLIENT001", new String[]{"*"}, true);
        var sub3 = createAndSave("sub_other", "OTHER_CLIENT", new String[]{"credit_deposit"}, true);

        var result = adapter.findActive(new ClientId("CLIENT001"), new EventKey("credit_deposit"));

        assertTrue(result.isPresent());
        var found = result.get();
        assertTrue(found.id().equals("sub_client001") || found.id().equals("sub_client001_alt"));
    }

    @Test
    void testFindActiveWithWildcardSubscription() {
        createAndSave("sub_wildcard", "CLIENT_T1", new String[]{"*"}, true);

        var result = adapter.findActive(new ClientId("CLIENT_T1"), new EventKey("any_event_key"));

        assertTrue(result.isPresent());
        assertEquals("sub_wildcard", result.get().id());
    }

    @Test
    void testFindActiveReturnsEmptyForNonexistentClient() {
        createAndSave("sub_other", "OTHER", new String[]{"event"}, true);

        var result = adapter.findActive(new ClientId("NONEXISTENT"), new EventKey("event"));

        assertFalse(result.isPresent());
    }

    @Test
    void testFindActiveIgnoresInactiveSubscriptions() {
        createAndSave("sub_inactive", "CLIENT_T1", new String[]{"credit_deposit"}, false);
        createAndSave("sub_active", "CLIENT_T1", new String[]{"payment_received"}, true);

        var result = adapter.findActive(new ClientId("CLIENT_T1"), new EventKey("credit_deposit"));

        assertFalse(result.isPresent());
    }

    @Test
    void testFindById() {
        createAndSave("sub_123", "CLIENT001", new String[]{"event.test"}, true);

        var result = adapter.findById("sub_123");

        assertTrue(result.isPresent());
        assertEquals("sub_123", result.get().id());
        assertEquals("CLIENT001", result.get().clientId().value());
    }

    @Test
    void testFindByIdReturnsEmptyForNonexistent() {
        var result = adapter.findById("nonexistent_sub");

        assertFalse(result.isPresent());
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
