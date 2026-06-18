package com.kirafintech.ledger.provider;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Provider B mock — VerboseRail.
 * Wire format: Authorization Bearer header, idempotency_token in body, amount as decimal string,
 * error as {"code": 4001, "message": "...", "details": []}.
 * For Day 3: in-memory mock that always returns COMPLETED immediately.
 * Deliberately different shape from SimpleRailAdapter to prove the abstraction works.
 */
@Component
public class VerboseRailAdapter implements FiatRailProvider {

    @Override
    public PaymentResult initiatePayment(PaymentParams params) {
        // Amount as decimal string (different shape from SimpleRail's integer cents)
        String amountDecimal = String.format("%.2f", params.amountMinorUnits() / 100.0);
        String ref = "VRAIL-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        return new PaymentResult(ref, PaymentStatus.SETTLED,
                """
                {"transaction_ref":"%s","state":"completed","amount":"%s"}
                """.formatted(ref, amountDecimal).trim());
    }

    @Override
    public PaymentResult getPaymentStatus(String providerRef) {
        return new PaymentResult(providerRef, PaymentStatus.SETTLED,
                """
                {"transaction_ref":"%s","state":"completed"}
                """.formatted(providerRef).trim());
    }

    @Override
    public void cancelPayment(String providerRef) {
        // Mock: no-op
    }

    @Override
    public String getProviderKey() {
        return "verbose_rail";
    }
}
