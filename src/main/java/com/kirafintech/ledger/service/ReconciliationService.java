package com.kirafintech.ledger.service;

import com.kirafintech.ledger.domain.Entry;
import com.kirafintech.ledger.domain.PayoutJob;
import com.kirafintech.ledger.repository.EntryRepository;
import com.kirafintech.ledger.repository.PayoutJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EOD reconciliation job that detects two mismatch types (CLAUDE.md §Reconciliation):
 *
 * 1. settled-with-no-entry: payout job COMPLETED but transfer still PENDING
 *    → rail moved money; our ledger status was not updated
 *
 * 2. entry-never-confirmed: entry confirmed=false older than threshold
 *    → ledger recorded a crypto movement that never reached confirmation threshold
 *    Auto-flag only; no auto-correct (ADR-002).
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final PayoutJobRepository payoutJobRepo;
    private final EntryRepository entryRepo;
    private final int unconfirmedThresholdMinutes;

    public ReconciliationService(PayoutJobRepository payoutJobRepo,
                                 EntryRepository entryRepo,
                                 @Value("${app.reconciliation.unconfirmed-threshold-minutes:15}") int unconfirmedThresholdMinutes) {
        this.payoutJobRepo = payoutJobRepo;
        this.entryRepo = entryRepo;
        this.unconfirmedThresholdMinutes = unconfirmedThresholdMinutes;
    }

    @Scheduled(fixedDelayString = "${app.reconciliation.interval-ms:300000}")
    @Transactional(readOnly = true)
    public ReconciliationReport run() {
        List<PayoutJob> settledNoEntry = payoutJobRepo.findSettledWithPendingTransfer();
        List<Entry> neverConfirmed = entryRepo.findStaleUnconfirmedEntries(unconfirmedThresholdMinutes);

        if (!settledNoEntry.isEmpty()) {
            for (PayoutJob job : settledNoEntry) {
                log.error("recon_mismatch type=settled_with_no_entry " +
                        "payout_job={} transfer={} provider={} — " +
                        "rail settled but ledger transfer still PENDING; manual review required",
                        job.getId(), job.getTransferId(), job.getProvider());
            }
        }

        if (!neverConfirmed.isEmpty()) {
            for (Entry entry : neverConfirmed) {
                log.warn("recon_mismatch type=entry_never_confirmed " +
                        "entry={} transfer={} account={} currency={} amount={} created_at={} — " +
                        "crypto deposit recorded but confirmation threshold never reached",
                        entry.getId(), entry.getTransferId(), entry.getAccountId(),
                        entry.getCurrency(), entry.getAmount(), entry.getCreatedAt());
            }
        }

        if (settledNoEntry.isEmpty() && neverConfirmed.isEmpty()) {
            log.info("recon_ok settled_no_entry=0 never_confirmed=0");
        }

        return new ReconciliationReport(settledNoEntry.size(), neverConfirmed.size(),
                settledNoEntry, neverConfirmed);
    }

    public record ReconciliationReport(
            int settledWithNoEntryCount,
            int entryNeverConfirmedCount,
            List<PayoutJob> settledWithNoEntry,
            List<Entry> entryNeverConfirmed
    ) {}
}
