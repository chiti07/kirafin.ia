package com.kirafintech.ledger.provider;

public record PaymentParams(
        String idempotencyKey,
        long amountMinorUnits,
        String currency,
        String destinationRef,
        String destinationType,
        String metadata
) {}
