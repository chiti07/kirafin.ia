package com.kirafintech.ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirafintech.ledger.application.port.in.LedgerPort;
import com.kirafintech.ledger.application.port.in.LedgerPort.FeeCommand;
import com.kirafintech.ledger.application.port.in.LedgerPort.PostInboundCreditCommand;
import com.kirafintech.ledger.application.port.in.OfframpPort;
import com.kirafintech.ledger.domain.Transfer;
import com.kirafintech.ledger.domain.enums.TransferType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OfframpService implements OfframpPort {

    private static final Logger log = LoggerFactory.getLogger(OfframpService.class);

    private final LedgerPort ledger;
    private final FeeCalculator feeCalc;
    private final RouteEngine routeEngine;
    private final ObjectMapper objectMapper;

    public OfframpService(LedgerPort ledger, FeeCalculator feeCalc,
                          RouteEngine routeEngine, ObjectMapper objectMapper) {
        this.ledger = ledger;
        this.feeCalc = feeCalc;
        this.routeEngine = routeEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * Converts a confirmed inbound USDC amount to USD, posts itemized fees,
     * and fires standing routes. All writes are in one @Transactional.
     * Idempotency key: inboundTransferId + ":offramp"
     */
    @Override
    @Transactional
    public void execute(UUID inboundTransferId, UUID accountId, long usdcMinorUnits, String chain) {
        String offrampKey = inboundTransferId + ":offramp";
        FeeCalculator.FeeBreakdown fees = feeCalc.calculate(usdcMinorUnits);

        log.info("offramp start inbound={} usdcMinorUnits={} grossCents={} netCents={} chain={}",
                inboundTransferId, usdcMinorUnits, fees.grossCents(), fees.netCents(), chain);

        String metadata = toJson(Map.of(
                "inbound_transfer_id", inboundTransferId.toString(),
                "chain", chain,
                "gross_usdc_minor_units", usdcMinorUnits,
                "exchange_rate", "1:1-mock"
        ));

        PostInboundCreditCommand cmd = new PostInboundCreditCommand(
                offrampKey,
                accountId,
                SystemAccounts.KIRA_LIQUIDITY_POOL,
                fees.netCents(),
                "USD",
                TransferType.CRYPTO,
                true,
                chain,
                metadata
        );
        Transfer creditTransfer = ledger.postInboundCredit(cmd);

        if (fees.platformFeeCents() > 0) {
            ledger.postFeeEntries(creditTransfer, accountId,
                    new FeeCommand("platform_fee", fees.platformFeeCents()));
        }
        if (fees.fixedFeeCents() > 0) {
            ledger.postFeeEntries(creditTransfer, accountId,
                    new FeeCommand("fixed_passthrough", fees.fixedFeeCents()));
        }

        List<Transfer> routesFired = routeEngine.evaluateAndFire(accountId, creditTransfer.getId());

        log.info("offramp complete creditTransfer={} netCents={} routesFired={}",
                creditTransfer.getId(), fees.netCents(), routesFired.size());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
