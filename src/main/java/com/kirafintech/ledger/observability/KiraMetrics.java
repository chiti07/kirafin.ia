package com.kirafintech.ledger.observability;

import com.kirafintech.ledger.domain.enums.EntryDirection;
import com.kirafintech.ledger.domain.enums.PayoutJobStatus;
import com.kirafintech.ledger.domain.enums.TransferStatus;
import com.kirafintech.ledger.repository.EntryRepository;
import com.kirafintech.ledger.repository.PayoutJobRepository;
import com.kirafintech.ledger.repository.TransferRepository;
import com.kirafintech.ledger.service.BalanceService;
import com.kirafintech.ledger.service.SystemAccounts;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Custom business metrics exposed at /actuator/prometheus.
 *
 * Counters  — increment on each event; cumulative over the process lifetime.
 * Gauges    — current snapshot; re-evaluated on each Prometheus scrape.
 *
 * Why these metrics?
 *   - transfers_created_total         → throughput signal; spike = traffic burst
 *   - fees_collected_cents_total      → revenue signal; drop = off-ramp broken
 *   - payout_jobs_failed_total        → settlement health; any value > 0 is critical
 *   - payout_jobs_pending_current     → queue depth; growth = worker lagging
 *   - double_entry_net_cents          → data integrity; must be 0 at all times
 *   - northwind_available_balance_cents → client balance; unexpected zero = route fired wrong
 */
@Component
public class KiraMetrics {

    private final MeterRegistry registry;
    private final TransferRepository transferRepo;
    private final PayoutJobRepository payoutJobRepo;
    private final EntryRepository entryRepo;
    private final BalanceService balanceService;

    // Counters are injected into services via this class
    private Counter transfersCreated;
    private Counter feesCollected;
    private Counter payoutJobsFailed;
    private Counter payoutJobsCompleted;
    private Counter idempotencyHits;

    public KiraMetrics(MeterRegistry registry,
                       TransferRepository transferRepo,
                       PayoutJobRepository payoutJobRepo,
                       EntryRepository entryRepo,
                       BalanceService balanceService) {
        this.registry = registry;
        this.transferRepo = transferRepo;
        this.payoutJobRepo = payoutJobRepo;
        this.entryRepo = entryRepo;
        this.balanceService = balanceService;
    }

    @PostConstruct
    public void registerMetrics() {
        // --- Counters (increment manually from services) ---
        transfersCreated = Counter.builder("kira.transfers.created.total")
                .description("Total inbound + outbound transfers created")
                .register(registry);

        feesCollected = Counter.builder("kira.fees.collected.cents.total")
                .description("Total platform fees collected in USD cents")
                .register(registry);

        payoutJobsFailed = Counter.builder("kira.payout.jobs.failed.total")
                .description("Total payout jobs that exhausted retries")
                .register(registry);

        payoutJobsCompleted = Counter.builder("kira.payout.jobs.completed.total")
                .description("Total payout jobs successfully settled")
                .register(registry);

        idempotencyHits = Counter.builder("kira.idempotency.hits.total")
                .description("Total duplicate requests stopped by idempotency guard")
                .register(registry);

        // --- Gauges (re-evaluated on every scrape) ---
        Gauge.builder("kira.payout.jobs.pending.current",
                        payoutJobRepo, r -> r.countByStatus(PayoutJobStatus.PENDING))
                .description("Current pending payout jobs — queue depth")
                .register(registry);

        Gauge.builder("kira.payout.jobs.processing.current",
                        payoutJobRepo, r -> r.countByStatus(PayoutJobStatus.PROCESSING))
                .description("Current in-flight payout jobs")
                .register(registry);

        Gauge.builder("kira.transfers.pending.current",
                        transferRepo, r -> r.countByStatus(TransferStatus.PENDING))
                .description("Transfers currently in PENDING status")
                .register(registry);

        Gauge.builder("kira.double.entry.net.cents",
                        entryRepo, r -> r.getGlobalDoubleEntryNet(EntryDirection.CREDIT))
                .description("Double-entry net (credits - debits). Must be 0. Any non-zero = data integrity failure.")
                .register(registry);

        Gauge.builder("kira.fees.platform.balance.cents",
                        this, self -> self.balanceService.getAvailableBalance(SystemAccounts.KIRA_FEE_ACCOUNT, "USD"))
                .description("Total fees accrued in the Kira Platform Fees account")
                .register(registry);

        Gauge.builder("kira.northwind.available.balance.cents",
                        this, self -> self.balanceService.getAvailableBalance(SystemAccounts.NORTHWIND_MAIN, "USD"))
                .description("Northwind Coffee Co. available USD balance in cents")
                .register(registry);
    }

    // --- Public methods called by services ---

    public void recordTransferCreated() {
        transfersCreated.increment();
    }

    public void recordFeesCollected(long cents) {
        feesCollected.increment(cents);
    }

    public void recordPayoutJobFailed() {
        payoutJobsFailed.increment();
    }

    public void recordPayoutJobCompleted() {
        payoutJobsCompleted.increment();
    }

    public void recordIdempotencyHit() {
        idempotencyHits.increment();
    }
}
