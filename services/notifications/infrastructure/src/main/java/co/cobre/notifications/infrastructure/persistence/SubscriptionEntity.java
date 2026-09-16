package co.cobre.notifications.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;


@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

    @Id
    private String id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "event_keys", nullable = false, columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] eventKeys;

    @Column(name = "url", nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "event_signature_key")
    private String eventSignatureKey;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public SubscriptionEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String[] getEventKeys() {
        return eventKeys;
    }

    public void setEventKeys(String[] eventKeys) {
        this.eventKeys = eventKeys;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEventSignatureKey() {
        return eventSignatureKey;
    }

    public void setEventSignatureKey(String eventSignatureKey) {
        this.eventSignatureKey = eventSignatureKey;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
