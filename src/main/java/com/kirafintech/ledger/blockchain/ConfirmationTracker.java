package com.kirafintech.ledger.blockchain;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of pending on-chain deposits awaiting confirmation threshold.
 * DB is authoritative: on crash/restart the watcher re-detects transactions and
 * the idempotency guard prevents double-credit (ADR-013).
 */
public class ConfirmationTracker {

    private final ConcurrentHashMap<String, PendingDeposit> pending = new ConcurrentHashMap<>();

    public void track(PendingDeposit deposit) {
        pending.put(deposit.signature(), deposit);
    }

    public void remove(String signature) {
        pending.remove(signature);
    }

    public Collection<PendingDeposit> getPending() {
        return pending.values();
    }

    public record PendingDeposit(
            String signature,
            long detectedAtSlot,
            UUID ledgerTransferId,
            long usdcAmountMinorUnits,
            String chain,
            UUID northwindAccountId
    ) {}
}
