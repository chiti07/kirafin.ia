package com.kirafintech.ledger.domain;

import com.kirafintech.ledger.domain.enums.EntryDirection;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only ledger entry. The ONLY permitted mutation after creation is flipping
 * confirmed=false to confirmed=true (via EntryRepository.confirmEntriesForTransfer).
 * No other UPDATE or DELETE ever touches this table.
 */
@Entity
@Table(name = "entries")
public class Entry {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "transfer_id", nullable = false, columnDefinition = "UUID", updatable = false)
    private UUID transferId;

    @Column(name = "account_id", nullable = false, columnDefinition = "UUID", updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private EntryDirection direction;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, length = 10, updatable = false)
    private String currency;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private String metadata;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    // --- getters ---

    public UUID getId() { return id; }
    public UUID getTransferId() { return transferId; }
    public UUID getAccountId() { return accountId; }
    public EntryDirection getDirection() { return direction; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public boolean isConfirmed() { return confirmed; }
    public Instant getCreatedAt() { return createdAt; }
    public String getMetadata() { return metadata; }

    // --- setters ---

    public void setId(UUID id) { this.id = id; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public void setDirection(EntryDirection direction) { this.direction = direction; }
    public void setAmount(long amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}