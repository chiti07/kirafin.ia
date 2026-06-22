package com.kirafintech.ledger.web;

import com.kirafintech.ledger.application.port.in.LedgerPort;
import com.kirafintech.ledger.application.port.in.LedgerPort.FeeCommand;
import com.kirafintech.ledger.application.port.in.LedgerPort.PostInboundCreditCommand;
import com.kirafintech.ledger.application.port.in.LedgerPort.PostOutboundDebitCommand;
import com.kirafintech.ledger.application.port.in.OfframpPort;
import com.kirafintech.ledger.domain.Transfer;
import com.kirafintech.ledger.domain.enums.TransferType;
import com.kirafintech.ledger.repository.TransferRepository;
import com.kirafintech.ledger.service.SystemAccounts;
import com.kirafintech.ledger.web.dto.CreateTransferRequest;
import com.kirafintech.ledger.web.dto.TransferResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final LedgerPort ledger;
    private final OfframpPort offrampPort;
    private final TransferRepository transferRepo;

    public TransferController(LedgerPort ledger, OfframpPort offrampPort,
                               TransferRepository transferRepo) {
        this.ledger = ledger;
        this.offrampPort = offrampPort;
        this.transferRepo = transferRepo;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(@Valid @RequestBody CreateTransferRequest req) {
        String direction = req.direction() == null || req.direction().isBlank() ? "INBOUND" : req.direction().toUpperCase();

        log.info("createTransfer account={} amount={} {} direction={}",
                req.accountId(), req.amountMinorUnits(), req.currency(), direction);

        boolean alreadyExists = transferRepo.existsByIdempotencyKey(req.idempotencyKey());
        TransferType type = TransferType.valueOf(req.type().toUpperCase());

        Transfer t;
        if ("OUTBOUND".equals(direction)) {
            validateOutbound(req);
            List<FeeCommand> fees = Collections.emptyList();
            PostOutboundDebitCommand cmd = new PostOutboundDebitCommand(
                    req.idempotencyKey(),
                    req.accountId(),
                    SystemAccounts.NORTHWIND_OMNIBUS,
                    req.amountMinorUnits(),
                    req.currency(),
                    type,
                    req.destinationRef(),
                    req.destinationType(),
                    req.provider(),
                    0L,
                    fees,
                    null
            );
            t = ledger.postOutboundDebit(cmd);
        } else if ("INBOUND".equals(direction)) {
            PostInboundCreditCommand cmd = new PostInboundCreditCommand(
                    req.idempotencyKey(),
                    req.accountId(),
                    SystemAccounts.CRYPTO_SUSPENSE,
                    req.amountMinorUnits(),
                    req.currency(),
                    type,
                    req.confirmed(),
                    req.chain(),
                    null
            );
            t = ledger.postInboundCredit(cmd);

            if (!alreadyExists && req.confirmed() && "USDC".equalsIgnoreCase(req.currency())) {
                log.info("createTransfer triggering off-ramp for confirmed USDC transfer={}", t.getId());
                offrampPort.execute(t.getId(), req.accountId(), req.amountMinorUnits(), req.chain());
            } else if (alreadyExists) {
                log.info("createTransfer idempotency hit key={} transfer={} — skipping off-ramp",
                        req.idempotencyKey(), t.getId());
            }
        } else {
            throw new IllegalArgumentException("Unsupported direction: " + direction + "; use INBOUND or OUTBOUND");
        }

        log.info("createTransfer done transfer={} status={} direction={}", t.getId(), t.getStatus(), direction);
        return ResponseEntity.status(alreadyExists ? 200 : 201).body(TransferResponse.from(t));
    }

    private void validateOutbound(CreateTransferRequest req) {
        if (req.destinationRef() == null || req.destinationRef().isBlank()
                || req.destinationType() == null || req.destinationType().isBlank()
                || req.provider() == null || req.provider().isBlank()) {
            throw new IllegalArgumentException("OUTBOUND transfers require destinationRef, destinationType, and provider");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable UUID id) {
        return transferRepo.findById(id)
                .map(t -> ResponseEntity.ok(TransferResponse.from(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Simulates the blockchain watcher reaching the confirmation threshold.
     * Flips entries confirmed=false → true, marks transfer COMPLETED,
     * then fires the off-ramp for USDC transfers (USDC → USD conversion + routes).
     * Idempotent: replaying on an already-confirmed transfer is a no-op.
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<TransferResponse> confirmTransfer(@PathVariable UUID id) {
        Transfer t = transferRepo.findById(id).orElse(null);
        if (t == null) return ResponseEntity.notFound().build();

        log.info("confirmTransfer id={} currentStatus={}", id, t.getStatus());

        ledger.confirmTransfer(id);

        Transfer confirmed = transferRepo.findById(id).orElseThrow();

        if ("USDC".equalsIgnoreCase(confirmed.getCurrency())) {
            String offrampKey = id + ":offramp";
            boolean offrampAlreadyRan = transferRepo.existsByIdempotencyKey(offrampKey);
            if (!offrampAlreadyRan) {
                log.info("confirmTransfer triggering off-ramp for USDC transfer={}", id);
                offrampPort.execute(id, confirmed.getAccountId(),
                        confirmed.getAmountMinorUnits(), confirmed.getChain());
            } else {
                log.info("confirmTransfer off-ramp already ran for transfer={} — skipping", id);
            }
        }

        return ResponseEntity.ok(TransferResponse.from(transferRepo.findById(id).orElseThrow()));
    }
}
