package com.kirafintech.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_account_id")
    private Account parent;

    @Column(name = "is_omnibus", nullable = false)
    private boolean omnibus;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(nullable = false, length = 10)
    private String currency = "USD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    // --- getters ---

    public UUID getId() { return id; }
    public Account getParent() { return parent; }
    public boolean isOmnibus() { return omnibus; }
    public String getClientName() { return clientName; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }

    // --- setters ---

    public void setId(UUID id) { this.id = id; }
    public void setParent(Account parent) { this.parent = parent; }
    public void setOmnibus(boolean omnibus) { this.omnibus = omnibus; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public void setCurrency(String currency) { this.currency = currency; }
}