package com.kirafintech.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, length = 512)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(nullable = false)
    private String payload;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public WebhookEvent() {}

    public WebhookEvent(String eventId, String provider, String payload, Instant processedAt) {
        this.eventId = eventId;
        this.provider = provider;
        this.payload = payload;
        this.processedAt = processedAt;
    }

    // --- getters ---

    public UUID getId() { return id; }
    public String getEventId() { return eventId; }
    public String getProvider() { return provider; }
    public String getPayload() { return payload; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getCreatedAt() { return createdAt; }
}