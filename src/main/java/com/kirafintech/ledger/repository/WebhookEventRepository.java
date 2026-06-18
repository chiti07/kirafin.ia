package com.kirafintech.ledger.repository;

import com.kirafintech.ledger.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    boolean existsByEventId(String eventId);
}
