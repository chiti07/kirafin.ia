package com.kirafintech.ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirafintech.ledger.application.port.in.LedgerPort;
import com.kirafintech.ledger.domain.Entry;
import com.kirafintech.ledger.domain.PayoutJob;
import com.kirafintech.ledger.domain.Transfer;
import com.kirafintech.ledger.domain.enums.*;
import com.kirafintech.ledger.observability.KiraMetrics;
import com.kirafintech.ledger.repository.EntryRepository;
import com.kirafintech.ledger.repository.PayoutJobRepository;
import com.kirafintech.ledger.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class LedgerService implements LedgerPort {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final TransferRepository transferRepo;
    private final EntryRepository entryRepo;
    private final PayoutJobRepository payoutJobRepo;
    private final BalanceService balanceService;
    private final ObjectMapper objectMapper;
    private final KiraMetrics metrics;

    public LedgerService(TransferRepository transferRepo, EntryRepository entryRepo,
                         PayoutJobRepository payoutJobRepo, BalanceService balanceService,
                         ObjectMapper objectMapper, KiraMetrics metrics) {
        this.transferRepo = transferRepo;
        this.entryRepo = entryRepo;
        this.payoutJobRepo = payoutJobRepo;
        this.balanceService = balanceService;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public Transfer postInboundCredit(PostInboundCreditCommand cmd) {
        return transferRepo.findByIdempotencyKey(cmd.idempotencyKey())
                .map(existing -> {
                    log.info("postInboundCredit idempotency hit key={} existingTransfer={}",
                            cmd.idempotencyKey(), existing.getId());
                    metrics.recordIdempotencyHit();
                    return existing;
                })
                .orElseGet(() -> {
                    Transfer t = new Transfer();
                    t.setIdempotencyKey(cmd.idempotencyKey());
                    t.setDirection(TransferDirection.INBOUND);
                    t.setType(cmd.type());
                    t.setStatus(cmd.confirmed() ? TransferStatus.COMPLETED : TransferStatus.PENDING);
                    t.setAccountId(cmd.accountId());
                    t.setAmountMinorUnits(cmd.amount());
                    t.setCurrency(cmd.currency());
                    t.setChain(cmd.chain());
                    transferRepo.save(t);

                    entryRepo.save(buildEntry(t.getId(), cmd.accountId(),
                            EntryDirection.CREDIT, cmd.amount(), cmd.currency(),
                            cmd.confirmed(), cmd.metadata()));
                    entryRepo.save(buildEntry(t.getId(), cmd.sourceAccountId(),
                            EntryDirection.DEBIT, cmd.amount(), cmd.currency(),
                            cmd.confirmed(), cmd.metadata()));

                    metrics.recordTransferCreated();
                    log.info("postInboundCredit created transfer={} account={} amount={} {} confirmed={}",
                            t.getId(), cmd.accountId(), cmd.amount(), cmd.currency(), cmd.confirmed());
                    return t;
                });
    }

    @Override
    @Transactional
    public Transfer postOutboundDebit(PostOutboundDebitCommand cmd) {
        return transferRepo.findByIdempotencyKey(cmd.idempotencyKey())
                .map(existing -> {
                    log.info("postOutboundDebit idempotency hit key={} existingTransfer={}",
                            cmd.idempotencyKey(), existing.getId());
                    metrics.recordIdempotencyHit();
                    return existing;
                })
                .orElseGet(() -> {
                    balanceService.lockAndVerifyBalance(cmd.accountId(), cmd.currency(),
                            cmd.amount() + cmd.totalFees());

                    Transfer t = new Transfer();
                    t.setIdempotencyKey(cmd.idempotencyKey());
                    t.setDirection(TransferDirection.OUTBOUND);
                    t.setType(cmd.type());
                    t.setStatus(TransferStatus.PENDING);
                    t.setAccountId(cmd.accountId());
                    t.setAmountMinorUnits(cmd.amount());
                    t.setCurrency(cmd.currency());
                    transferRepo.save(t);

                    entryRepo.save(buildEntry(t.getId(), cmd.accountId(),
                            EntryDirection.DEBIT, cmd.amount(), cmd.currency(),
                            true, cmd.metadata()));
                    entryRepo.save(buildEntry(t.getId(), cmd.destinationAccountId(),
                            EntryDirection.CREDIT, cmd.amount(), cmd.currency(),
                            true, cmd.metadata()));

                    for (FeeCommand fee : cmd.fees()) {
                        postFeeEntries(t, cmd.accountId(), fee);
                    }

                    payoutJobRepo.save(buildPayoutJob(t, cmd));

                    metrics.recordTransferCreated();
                    log.info("postOutboundDebit created transfer={} account={} amount={} {} provider={}",
                            t.getId(), cmd.accountId(), cmd.amount(), cmd.currency(), cmd.provider());
                    return t;
                });
    }

    @Override
    @Transactional
    public void postFeeEntries(Transfer parentTransfer, UUID clientAccountId, FeeCommand fee) {
        String meta = toJson(Map.of("fee_type", fee.type()));
        entryRepo.save(buildEntry(parentTransfer.getId(), clientAccountId,
                EntryDirection.DEBIT, fee.amount(), "USD", true, meta));
        entryRepo.save(buildEntry(parentTransfer.getId(), SystemAccounts.KIRA_FEE_ACCOUNT,
                EntryDirection.CREDIT, fee.amount(), "USD", true, meta));
        log.debug("postFeeEntries transfer={} type={} amount={}", parentTransfer.getId(), fee.type(), fee.amount());
    }

    @Override
    @Transactional
    public void confirmTransfer(UUID transferId) {
        int updated = entryRepo.confirmEntriesForTransfer(transferId);
        if (updated > 0) {
            transferRepo.updateStatus(transferId, TransferStatus.COMPLETED);
            log.info("confirmTransfer transfer={} entries confirmed={}", transferId, updated);
        } else {
            log.warn("confirmTransfer transfer={} — no pending entries found", transferId);
        }
    }

    // --- helpers ---

    private Entry buildEntry(UUID transferId, UUID accountId, EntryDirection direction,
                              long amount, String currency, boolean confirmed, String metadata) {
        Entry e = new Entry();
        e.setTransferId(transferId);
        e.setAccountId(accountId);
        e.setDirection(direction);
        e.setAmount(amount);
        e.setCurrency(currency);
        e.setConfirmed(confirmed);
        e.setMetadata(metadata);
        return e;
    }

    private PayoutJob buildPayoutJob(Transfer t, PostOutboundDebitCommand cmd) {
        PayoutJob job = new PayoutJob();
        job.setTransferId(t.getId());
        job.setIdempotencyKey(t.getId().toString());
        job.setProvider(cmd.provider());
        job.setPayload(toJson(Map.of(
                "destinationRef", cmd.destinationRef(),
                "destinationType", cmd.destinationType(),
                "amountMinorUnits", cmd.amount(),
                "currency", cmd.currency()
        )));
        return job;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
