package com.kirafintech.ledger.domain;

import com.kirafintech.ledger.domain.enums.TransferDirection;
import com.kirafintech.ledger.domain.enums.TransferStatus;
import com.kirafintech.ledger.domain.enums.TransferType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 512)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransferType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "account_id", nullable = false, columnDefinition = "UUID")
    private UUID accountId;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(length = 50)
    private String chain;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = TransferStatus.PENDING;
    }

    // --- getters ---

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public TransferDirection getDirection() { return direction; }
    public TransferType getType() { return type; }
    public TransferStatus getStatus() { return status; }
    public UUID getAccountId() { return accountId; }
    public long getAmountMinorUnits() { return amountMinorUnits; }
    public String getCurrency() { return currency; }
    public String getChain() { return chain; }
    public Instant getCreatedAt() { return createdAt; }

    // --- setters ---

    public void setId(UUID id) { this.id = id; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setDirection(TransferDirection direction) { this.direction = direction; }
    public void setType(TransferType type) { this.type = type; }
    public void setStatus(TransferStatus status) { this.status = status; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public void setAmountMinorUnits(long amountMinorUnits) { this.amountMinorUnits = amountMinorUnits; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setChain(String chain) { this.chain = chain; }
}