package com.kirafintech.ledger.application.port.in;

import com.kirafintech.ledger.domain.Transfer;
import com.kirafintech.ledger.domain.enums.TransferType;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port: all operations that move money through the double-entry ledger.
 * Services in the application layer implement this; adapters (REST, Solana watcher)
 * depend on it — never on the concrete service class.
 */
public interface LedgerPort {

    Transfer postInboundCredit(PostInboundCreditCommand cmd);

    Transfer postOutboundDebit(PostOutboundDebitCommand cmd);

    void postFeeEntries(Transfer parentTransfer, UUID clientAccountId, FeeCommand fee);

    void confirmTransfer(UUID transferId);

    // --- command records (input contract of this port) ---

    record PostInboundCreditCommand(
            String idempotencyKey,
            UUID accountId,
            UUID sourceAccountId,
            long amount,
            String currency,
            TransferType type,
            boolean confirmed,
            String chain,
            String metadata
    ) {}

    record PostOutboundDebitCommand(
            String idempotencyKey,
            UUID accountId,
            UUID destinationAccountId,
            long amount,
            String currency,
            TransferType type,
            String destinationRef,
            String destinationType,
            String provider,
            long totalFees,
            List<FeeCommand> fees,
            String metadata
    ) {}

    record FeeCommand(String type, long amount) {}
}
