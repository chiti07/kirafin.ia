package com.kirafintech.ledger.domain;

import com.kirafintech.ledger.domain.enums.PayoutJobStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_jobs")
public class PayoutJob {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "transfer_id", nullable = false, columnDefinition = "UUID", unique = true)
    private UUID transferId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 512)
    private String idempotencyKey;

    @Column(nullable = false, length = 100)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutJobStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private String payload;

    @Column
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = PayoutJobStatus.PENDING;
        if (nextAttemptAt == null) nextAttemptAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // --- getters ---

    public UUID getId() { return id; }
    public UUID getTransferId() { return transferId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getProvider() { return provider; }
    public PayoutJobStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getPayload() { return payload; }
    public String getResult() { return result; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // --- setters ---

    public void setId(UUID id) { this.id = id; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setStatus(PayoutJobStatus status) { this.status = status; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public void setPayload(String payload) { this.payload = payload; }
    public void setResult(String result) { this.result = result; }
}