package com.kirafintech.ledger.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Polygon Amoy testnet mock adapter.
 * Wire format: EIP-1559 JSON-RPC; amount in USDT minor units (6 decimals).
 * USD cents → USDT minor units: cents × 10,000 (1 USD = 1 USDT at 1:1 mock rate).
 * For Day 4: in-memory mock that returns a plausible Polygon tx hash immediately.
 */
@Component
public class PolygonOutboundAdapter implements FiatRailProvider {

    private static final Logger log = LoggerFactory.getLogger(PolygonOutboundAdapter.class);

    @Override
    public PaymentResult initiatePayment(PaymentParams params) {
        long usdtMinorUnits = params.amountMinorUnits() * 10_000L;
        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "") +
                        UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        log.info("polygon_outbound destinationRef={} usdtMinorUnits={} txHash={}",
                params.destinationRef(), usdtMinorUnits, txHash);
        return new PaymentResult(txHash, PaymentStatus.SETTLED,
                """
                {"tx_hash":"%s","network":"polygon-amoy","usdt_minor_units":%d,"status":"submitted"}
                """.formatted(txHash, usdtMinorUnits).trim());
    }

    @Override
    public PaymentResult getPaymentStatus(String providerRef) {
        return new PaymentResult(providerRef, PaymentStatus.SETTLED,
                """
                {"tx_hash":"%s","status":"confirmed"}
                """.formatted(providerRef).trim());
    }

    @Override
    public void cancelPayment(String providerRef) {
        // Blockchain txs are not cancellable once submitted
        log.warn("polygon_cancel_ignored txHash={} — on-chain txs are irreversible", providerRef);
    }

    @Override
    public String getProviderKey() {
        return "polygon";
    }
}
