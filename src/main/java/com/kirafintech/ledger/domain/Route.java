package com.kirafintech.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routes")
public class Route {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, columnDefinition = "UUID")
    private UUID accountId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "destination_type", nullable = false, length = 50)
    private String destinationType;

    @Column(name = "destination_ref", nullable = false, length = 512)
    private String destinationRef;

    @Column(name = "amount_strategy", nullable = false, length = 50)
    private String amountStrategy;

    @Column(name = "amount_value", nullable = false)
    private long amountValue;

    @Column(length = 100)
    private String provider;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    // --- getters ---

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public String getDestinationType() { return destinationType; }
    public String getDestinationRef() { return destinationRef; }
    public String getAmountStrategy() { return amountStrategy; }
    public long getAmountValue() { return amountValue; }
    public String getProvider() { return provider; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }

    // --- setters ---

    public void setId(UUID id) { this.id = id; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public void setName(String name) { this.name = name; }
    public void setActive(boolean active) { this.active = active; }
    public void setDestinationType(String destinationType) { this.destinationType = destinationType; }
    public void setDestinationRef(String destinationRef) { this.destinationRef = destinationRef; }
    public void setAmountStrategy(String amountStrategy) { this.amountStrategy = amountStrategy; }
    public void setAmountValue(long amountValue) { this.amountValue = amountValue; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setCurrency(String currency) { this.currency = currency; }
}
