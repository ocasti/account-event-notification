package co.cobre.notifications.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivery attempt persistence entity.
 */
@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttemptEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "cycle", nullable = false)
    private int cycle;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "origin", nullable = false)
    @Enumerated(EnumType.STRING)
    private AttemptOriginEntity origin;

    public DeliveryAttemptEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public int getCycle() {
        return cycle;
    }

    public void setCycle(int cycle) {
        this.cycle = cycle;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public void setClaimedBy(String claimedBy) {
        this.claimedBy = claimedBy;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public AttemptOriginEntity getOrigin() {
        return origin;
    }

    public void setOrigin(AttemptOriginEntity origin) {
        this.origin = origin;
    }
}
