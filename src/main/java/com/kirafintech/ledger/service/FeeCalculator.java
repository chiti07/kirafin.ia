package com.kirafintech.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Converts USDC minor units to USD cents and calculates itemized fees.
 * All arithmetic is BIGINT (long) — no float, no BigDecimal (ADR-001).
 *
 * USDC (Solana): 6 decimal places → 1 USDC = 1,000,000 minor units
 * USD: 2 decimal places → 1 USD = 100 cents
 * Conversion: USD cents = USDC minor units / 10,000
 *
 * BPS truncation: (amount * bps) / 10,000 rounds down — to client's favor.
 */
@Component
public class FeeCalculator {

    private static final Logger log = LoggerFactory.getLogger(FeeCalculator.class);

    private final long platformFeeBps;
    private final long fixedFeeCents;

    public FeeCalculator(@Value("${app.offramp.platform-fee-bps}") long platformFeeBps,
                         @Value("${app.offramp.fixed-fee-cents}") long fixedFeeCents) {
        this.platformFeeBps = platformFeeBps;
        this.fixedFeeCents = fixedFeeCents;
    }

    public FeeBreakdown calculate(long usdcMinorUnits) {
        long grossCents = usdcMinorUnits / 10_000L;
        long platformFee = (grossCents * platformFeeBps) / 10_000L;
        long fixedFee = fixedFeeCents;
        long totalFee = platformFee + fixedFee;
        long netCents = grossCents - totalFee;
        FeeBreakdown breakdown = new FeeBreakdown(grossCents, platformFee, fixedFee, totalFee, netCents);
        log.debug("fees usdcMinorUnits={} grossCents={} platform={} fixed={} net={}",
                usdcMinorUnits, grossCents, platformFee, fixedFee, netCents);
        return breakdown;
    }

    public record FeeBreakdown(
            long grossCents,
            long platformFeeCents,
            long fixedFeeCents,
            long totalFeeCents,
            long netCents
    ) {}
}
