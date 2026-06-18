package com.kirafintech.ledger.provider;

public record PaymentResult(
        String providerRef,
        PaymentStatus status,
        String raw
) {}
