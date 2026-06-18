package com.kirafintech.ledger.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirafintech.ledger.domain.WebhookEvent;
import com.kirafintech.ledger.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookEventRepository webhookEventRepo;
    private final ObjectMapper objectMapper;

    public WebhookController(WebhookEventRepository webhookEventRepo, ObjectMapper objectMapper) {
        this.webhookEventRepo = webhookEventRepo;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String provider,
            @RequestHeader(value = "X-Event-Id", required = false) String eventId,
            @RequestBody Map<String, Object> payload) throws JsonProcessingException {

        if (eventId == null || eventId.isBlank()) {
            eventId = provider + ":" + Instant.now().toEpochMilli();
        }

        if (webhookEventRepo.existsByEventId(eventId)) {
            log.info("webhook idempotency hit provider={} eventId={}", provider, eventId);
            return ResponseEntity.ok().build();
        }

        webhookEventRepo.save(new WebhookEvent(eventId, provider,
                objectMapper.writeValueAsString(payload), Instant.now()));

        log.info("webhook received provider={} eventId={}", provider, eventId);
        return ResponseEntity.accepted().build();
    }
}
