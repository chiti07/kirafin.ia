package com.kirafintech.ledger.web;

import com.kirafintech.ledger.application.port.in.LedgerPort;
import com.kirafintech.ledger.application.port.in.LedgerPort.PostInboundCreditCommand;
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
        log.info("createTransfer account={} amount={} {} confirmed={}",
                req.accountId(), req.amountMinorUnits(), req.currency(), req.confirmed());

        boolean alreadyExists = transferRepo.existsByIdempotencyKey(req.idempotencyKey());

        TransferType type = TransferType.valueOf(req.type().toUpperCase());

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
        Transfer t = ledger.postInboundCredit(cmd);

        if (!alreadyExists && req.confirmed() && "USDC".equalsIgnoreCase(req.currency())) {
            log.info("createTransfer triggering off-ramp for confirmed USDC transfer={}", t.getId());
            offrampPort.execute(t.getId(), req.accountId(), req.amountMinorUnits(), req.chain());
        } else if (alreadyExists) {
            log.info("createTransfer idempotency hit key={} transfer={} — skipping off-ramp",
                    req.idempotencyKey(), t.getId());
        }

        log.info("createTransfer done transfer={} status={}", t.getId(), t.getStatus());
        return ResponseEntity.status(alreadyExists ? 200 : 201).body(TransferResponse.from(t));
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
