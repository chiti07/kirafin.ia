package com.kirafintech.ledger.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirafintech.ledger.domain.PayoutJob;
import com.kirafintech.ledger.domain.enums.PayoutJobStatus;
import com.kirafintech.ledger.domain.enums.TransferStatus;
import com.kirafintech.ledger.provider.FiatRailProviderRegistry;
import com.kirafintech.ledger.provider.PaymentParams;
import com.kirafintech.ledger.provider.PaymentResult;
import com.kirafintech.ledger.repository.PayoutJobRepository;
import com.kirafintech.ledger.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class PayoutWorker {

    private static final Logger log = LoggerFactory.getLogger(PayoutWorker.class);

    private final PayoutJobRepository payoutJobRepo;
    private final TransferRepository transferRepo;
    private final FiatRailProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final int batchSize;

    public PayoutWorker(PayoutJobRepository payoutJobRepo, TransferRepository transferRepo,
                        FiatRailProviderRegistry providerRegistry, ObjectMapper objectMapper,
                        @Value("${app.payout-worker.max-attempts}") int maxAttempts,
                        @Value("${app.payout-worker.batch-size}") int batchSize) {
        this.payoutJobRepo = payoutJobRepo;
        this.transferRepo = transferRepo;
        this.providerRegistry = providerRegistry;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.payout-worker.poll-interval-ms}")
    @Transactional
    public void processPayouts() {
        // FOR UPDATE SKIP LOCKED — safe for multiple worker instances
        List<PayoutJob> jobs = payoutJobRepo.claimPendingJobs(batchSize);
        if (!jobs.isEmpty()) {
            log.debug("Payout worker claimed {} jobs", jobs.size());
        }
        for (PayoutJob job : jobs) {
            processJob(job);
        }
    }

    private void processJob(PayoutJob job) {
        job.setStatus(PayoutJobStatus.PROCESSING);
        job.setAttempts(job.getAttempts() + 1);
        payoutJobRepo.save(job);

        try {
            PaymentParams params = buildPaymentParams(job);
            PaymentResult result = providerRegistry.get(job.getProvider()).initiatePayment(params);

            job.setStatus(PayoutJobStatus.COMPLETED);
            job.setResult(objectMapper.writeValueAsString(Map.of(
                    "providerRef", result.providerRef(),
                    "status", result.status().name()
            )));
            transferRepo.updateStatus(job.getTransferId(), TransferStatus.COMPLETED);
            log.info("Payout job {} completed via {}: ref={}", job.getId(), job.getProvider(), result.providerRef());

        } catch (Exception e) {
            log.error("Payout job {} failed attempt {}: {}", job.getId(), job.getAttempts(), e.getMessage());
            if (job.getAttempts() >= maxAttempts) {
                job.setStatus(PayoutJobStatus.FAILED);
                transferRepo.updateStatus(job.getTransferId(), TransferStatus.FAILED);
            } else {
                job.setStatus(PayoutJobStatus.PENDING);
                // Exponential backoff, capped at 1 hour
                long backoffSecs = Math.min(30L * (long) Math.pow(2, job.getAttempts()), 3600L);
                job.setNextAttemptAt(Instant.now().plusSeconds(backoffSecs));
            }
        }
        payoutJobRepo.save(job);
    }

    private PaymentParams buildPaymentParams(PayoutJob job) {
        try {
            Map<String, Object> payload = objectMapper.readValue(job.getPayload(),
                    new TypeReference<>() {});
            long amount = ((Number) payload.get("amountMinorUnits")).longValue();
            String currency = (String) payload.getOrDefault("currency", "USD");
            String destinationRef = (String) payload.getOrDefault("destinationRef", "");
            String destinationType = (String) payload.getOrDefault("destinationType", "fiat_ach");
            return new PaymentParams(job.getIdempotencyKey(), amount, currency,
                    destinationRef, destinationType, job.getPayload());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse payout job payload: " + e.getMessage(), e);
        }
    }
}
