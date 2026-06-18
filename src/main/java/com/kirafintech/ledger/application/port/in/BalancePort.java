package com.kirafintech.ledger.application.port.in;

import java.util.UUID;

/**
 * Inbound port: balance queries and the SELECT FOR UPDATE balance guard.
 * lockAndVerifyBalance MUST be called from within an existing @Transactional context.
 */
public interface BalancePort {

    long getAvailableBalance(UUID accountId, String currency);

    long getPendingBalance(UUID accountId, String currency);

    void lockAndVerifyBalance(UUID accountId, String currency, long requiredAmount);
}
