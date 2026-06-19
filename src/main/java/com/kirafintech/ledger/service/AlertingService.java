package com.kirafintech.ledger.service;

import com.kirafintech.ledger.domain.PayoutJob;
import com.kirafintech.ledger.domain.Transfer;
import com.kirafintech.ledger.domain.enums.EntryDirection;
import com.kirafintech.ledger.repository.EntryRepository;
import com.kirafintech.ledger.repository.PayoutJobRepository;
import com.kirafintech.ledger.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Business alert signals implemented as structured log lines.
 *
 * Alerts are the on-call-actionable subset of all possible signals.
 * Rationale for each alert is inline — the goal is proactive detection
 * before an outage hurts money.
 *
 * Infrastructure metrics (CPU, RAM, DB connections) are logged here as
 * a low-overhead proxy; a real deployment would export these to Datadog/Prometheus.
 *
 * Alert inventory (see DECISIONS.md ADR-017):
 *   CRITICAL alert_failed_settlement      — payout job failed after max retries
 *   CRITICAL alert_balance_drift          — double-entry net != 0 (data integrity)
 *   CRITICAL alert_reconciliation_break   — settled-with-no-entry mismatch
 *   WARN     alert_stuck_pending          — transfer pending > 30 min
 *   WARN     alert_confirmation_backlog   — unconfirmed crypto entries > 15 min
 *   WARN     alert_db_connection_pressure — connection pool close to exhaustion
 */
@Service
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);

    private final PayoutJobRepository payoutJobRepo;
    private final TransferRepository transferRepo;
    private final EntryRepository entryRepo;
    private final ReconciliationService reconciliationService;
    private final DataSource dataSource;
    private final int stuckPendingMinutes;
    private final int unconfirmedThresholdMinutes;

    public AlertingService(PayoutJobRepository payoutJobRepo,
                           TransferRepository transferRepo,
                           EntryRepository entryRepo,
                           ReconciliationService reconciliationService,
                           DataSource dataSource,
                           @Value("${app.alerting.stuck-pending-minutes:30}") int stuckPendingMinutes,
                           @Value("${app.alerting.unconfirmed-threshold-minutes:15}") int unconfirmedThresholdMinutes) {
        this.payoutJobRepo = payoutJobRepo;
        this.transferRepo = transferRepo;
        this.entryRepo = entryRepo;
        this.reconciliationService = reconciliationService;
        this.dataSource = dataSource;
        this.stuckPendingMinutes = stuckPendingMinutes;
        this.unconfirmedThresholdMinutes = unconfirmedThresholdMinutes;
    }

    @Scheduled(fixedDelayString = "${app.alerting.interval-ms:60000}")
    @Transactional(readOnly = true)
    public void evaluate() {
        try {
            checkFailedSettlements();
            checkStuckPendingTransfers();
            checkConfirmationBacklog();
            checkBalanceDrift();
            checkReconciliation();
            checkDbConnectionHealth();
        } catch (Exception e) {
            // Must never propagate — scheduled thread would die permanently
            log.error("alert_evaluation_error message={}", e.getMessage(), e);
        }
    }

    private void checkFailedSettlements() {
        Instant since = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<PayoutJob> failed = payoutJobRepo.findFailedSince(since);
        if (!failed.isEmpty()) {
            for (PayoutJob job : failed) {
                // CRITICAL: money was debited from account but never reached the rail
                log.error("alert_failed_settlement payout_job={} transfer={} provider={} attempts={} — " +
                        "debit posted to ledger but payout exhausted retries; requires manual intervention",
                        job.getId(), job.getTransferId(), job.getProvider(), job.getAttempts());
            }
        }
    }

    private void checkStuckPendingTransfers() {
        Instant threshold = Instant.now().minus(stuckPendingMinutes, ChronoUnit.MINUTES);
        List<Transfer> stuck = transferRepo.findStuckPending(threshold);
        if (!stuck.isEmpty()) {
            // WARN: transfer older than threshold still in PENDING — payout worker may be stuck
            log.warn("alert_stuck_pending count={} threshold_minutes={} — " +
                    "payout worker may be lagging or blocked; check queue depth and worker health",
                    stuck.size(), stuckPendingMinutes);
            stuck.forEach(t -> log.warn("alert_stuck_pending_detail transfer={} created_at={}",
                    t.getId(), t.getCreatedAt()));
        }
    }

    private void checkConfirmationBacklog() {
        List<?> stale = entryRepo.findStaleUnconfirmedEntries(unconfirmedThresholdMinutes);
        if (!stale.isEmpty()) {
            // WARN: crypto deposit detected on-chain but never confirmed — chain RPC may be lagging
            log.warn("alert_confirmation_backlog count={} threshold_minutes={} — " +
                    "check blockchain watcher connectivity and confirmation threshold config",
                    stale.size(), unconfirmedThresholdMinutes);
        }
    }

    private void checkBalanceDrift() {
        long net = entryRepo.getGlobalDoubleEntryNet(EntryDirection.CREDIT);
        if (net != 0L) {
            // CRITICAL: double-entry invariant broken — data integrity failure
            log.error("alert_balance_drift net={} — double-entry invariant violated; " +
                    "CREDIT sum != DEBIT sum across entries table. Halt new transactions and investigate.",
                    net);
        }
    }

    private void checkReconciliation() {
        ReconciliationService.ReconciliationReport report = reconciliationService.run();
        if (report.settledWithNoEntryCount() > 0) {
            log.error("alert_reconciliation_break type=settled_with_no_entry count={} — " +
                    "rail settled but ledger has no completed record; revenue leakage risk",
                    report.settledWithNoEntryCount());
        }
        if (report.entryNeverConfirmedCount() > 0) {
            log.warn("alert_reconciliation_break type=entry_never_confirmed count={} — " +
                    "crypto deposits recorded but never confirmed; funds are inaccessible",
                    report.entryNeverConfirmedCount());
        }
    }

    private void checkDbConnectionHealth() {
        // Proxy metric: attempt to get a connection; log pool pressure if it takes too long
        long start = System.currentTimeMillis();
        try (Connection ignored = dataSource.getConnection()) {
            long ms = System.currentTimeMillis() - start;
            if (ms > 500) {
                log.warn("alert_db_connection_pressure acquisition_ms={} — " +
                        "connection pool under pressure; consider increasing pool size or scaling DB",
                        ms);
            } else {
                log.debug("infra_db_connection_ok acquisition_ms={}", ms);
            }
        } catch (Exception e) {
            log.error("alert_db_connection_failed message={}", e.getMessage());
        }
    }
}
