package com.kirafintech.ledger.web.dto;

import java.util.List;

public record DashboardResponse(
        List<AccountSummary> accounts,
        List<RecentTransfer> recentTransfers,
        FeesSummary fees,
        PayoutJobCounts payoutJobs,
        ReconciliationSummary reconciliation
) {
    public record AccountSummary(
            String id,
            String name,
            long availableCents,
            long pendingCents,
            String currency
    ) {}

    public record RecentTransfer(
            String id,
            String direction,
            String type,
            String status,
            long amountMinorUnits,
            String currency,
            String createdAt
    ) {}

    public record FeesSummary(
            long totalFeesCollectedCents
    ) {}

    public record PayoutJobCounts(
            long pending,
            long processing,
            long completed,
            long failed
    ) {}

    public record ReconciliationSummary(
            int settledWithNoEntryCount,
            int entryNeverConfirmedCount
    ) {}
}
