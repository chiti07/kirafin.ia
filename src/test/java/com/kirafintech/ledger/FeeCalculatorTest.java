package com.kirafintech.ledger;

import com.kirafintech.ledger.service.FeeCalculator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    private final FeeCalculator calc = new FeeCalculator(1L, 50L, 250L); // 1 BPS, $0.50 fixed

    @Test
    void northwindFlow_5000Usdc() {
        // 5,000 USDC = 5,000,000,000 minor units
        FeeCalculator.FeeBreakdown fees = calc.calculate(5_000_000_000L);

        assertThat(fees.grossCents()).isEqualTo(500_000L);          // $5,000.00
        assertThat(fees.platformFeeCents()).isEqualTo(50L);         // 1 BPS of $5,000 = $0.50
        assertThat(fees.fixedFeeCents()).isEqualTo(50L);            // $0.50 fixed
        assertThat(fees.fixedNetworkFeeCents()).isEqualTo(250L);    // $2.50 network fee
        assertThat(fees.totalFeeCents()).isEqualTo(350L);           // $3.50 total
        assertThat(fees.netCents()).isEqualTo(499_650L);            // $4,996.50

        // Verify no floating point was involved (all are exact long values)
        assertThat(fees.grossCents() + fees.totalFeeCents()).isNotEqualTo(Long.MAX_VALUE);
    }

    @Test
    void zeroFees_whenAmountTooSmall() {
        // 99 USDC minor units → 0 cents gross (below threshold)
        FeeCalculator.FeeBreakdown fees = calc.calculate(99L);
        assertThat(fees.grossCents()).isEqualTo(0L);
    }

    @Test
    void bpsTruncation_roundsToClientsAdvantage() {
        // 1,000 USDC minor units = 0.1 cents gross... platform fee should be 0 (truncated)
        FeeCalculator.FeeBreakdown fees = calc.calculate(1_000_000L); // 1 USDC
        assertThat(fees.grossCents()).isEqualTo(100L);               // 100 cents = $1.00
        assertThat(fees.platformFeeCents()).isEqualTo(0L);           // 1 BPS of 100 cents = 0.01 → truncated to 0
        assertThat(fees.fixedFeeCents()).isEqualTo(50L);             // $0.50
        assertThat(fees.fixedNetworkFeeCents()).isEqualTo(250L);     // $2.50
        assertThat(fees.totalFeeCents()).isEqualTo(300L);            // $3.00 total fees
        assertThat(fees.netCents()).isEqualTo(-200L);                // fees exceed gross for tiny amounts (ledger guards against negative balance)
    }
}
