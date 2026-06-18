package com.kirafintech.ledger.provider;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Provider A mock — SimpleRail.
 * Wire format: X-Api-Key header, X-Idempotency-Key header, amount as integer cents,
 * error as {"error": "message"}.
 * For Day 3: in-memory mock that always returns SETTLED immediately.
 */
@Component
public class SimpleRailAdapter implements FiatRailProvider {

    @Override
    public PaymentResult initiatePayment(PaymentParams params) {
        // Mock: simulate a successful ACH initiation
        String ref = "SIMPLERAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentResult(ref, PaymentStatus.SETTLED,
                """
                {"payment_id":"%s","status":"settled","amount":%d}
                """.formatted(ref, params.amountMinorUnits()).trim());
    }

    @Override
    public PaymentResult getPaymentStatus(String providerRef) {
        return new PaymentResult(providerRef, PaymentStatus.SETTLED,
                """
                {"payment_id":"%s","status":"settled"}
                """.formatted(providerRef).trim());
    }

    @Override
    public void cancelPayment(String providerRef) {
        // Mock: no-op
    }

    @Override
    public String getProviderKey() {
        return "simple_rail";
    }
}
