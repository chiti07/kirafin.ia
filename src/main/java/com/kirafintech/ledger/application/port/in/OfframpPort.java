package com.kirafintech.ledger.application.port.in;

import java.util.UUID;

/**
 * Inbound port: USDC → USD conversion, fee posting, and route trigger.
 * Idempotency key: inboundTransferId + ":offramp"
 */
public interface OfframpPort {

    void execute(UUID inboundTransferId, UUID accountId, long usdcMinorUnits, String chain);
}
