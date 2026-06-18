package com.kirafintech.ledger.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirafintech.ledger.domain.Transfer;
import com.kirafintech.ledger.domain.enums.TransferType;
import com.kirafintech.ledger.repository.TransferRepository;
import com.kirafintech.ledger.application.port.in.LedgerPort;
import com.kirafintech.ledger.application.port.in.LedgerPort.PostInboundCreditCommand;
import com.kirafintech.ledger.application.port.in.OfframpPort;
import com.kirafintech.ledger.service.SystemAccounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Polls Solana devnet for inbound USDC transfers to the watch address.
 * Uses HTTP polling (not websocket) for crash-recoverable design (ADR-012).
 * In-memory confirmation tracker backed by DB as authoritative state (ADR-013).
 */
@Component
public class SolanaWatcher {

    private static final Logger log = LoggerFactory.getLogger(SolanaWatcher.class);

    // Solana USDC devnet mint address
    private static final String USDC_DEVNET_MINT = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU";

    private final SolanaRpcClient rpc;
    private final ConfirmationTracker tracker;
    private final LedgerPort ledger;
    private final OfframpPort offramp;
    private final TransferRepository transferRepo;
    private final ObjectMapper objectMapper;
    private final String watchAddress;
    private final long confirmationThreshold;
    private final String usdcMint;

    private volatile String lastSignature = null;

    public SolanaWatcher(SolanaRpcClient rpc,
                         LedgerPort ledger,
                         OfframpPort offramp,
                         TransferRepository transferRepo,
                         ObjectMapper objectMapper,
                         @Value("${app.solana.watch-address:}") String watchAddress,
                         @Value("${app.solana.confirmation-threshold:32}") long confirmationThreshold,
                         @Value("${app.solana.usdc-mint:" + USDC_DEVNET_MINT + "}") String usdcMint) {
        this.rpc = rpc;
        this.tracker = new ConfirmationTracker();
        this.ledger = ledger;
        this.offramp = offramp;
        this.transferRepo = transferRepo;
        this.objectMapper = objectMapper;
        this.watchAddress = watchAddress;
        this.confirmationThreshold = confirmationThreshold;
        this.usdcMint = usdcMint;
    }

    @Scheduled(fixedDelayString = "${app.solana.poll-interval-ms:5000}")
    public void pollForDeposits() {
        if (watchAddress == null || watchAddress.isBlank()) return;

        try {
            List<SolanaRpcClient.SignatureInfo> newSigs =
                    rpc.getSignaturesForAddress(watchAddress, lastSignature);

            for (SolanaRpcClient.SignatureInfo sig : newSigs) {
                if (!sig.hasError()) {
                    processNewSignature(sig);
                }
            }

            if (!newSigs.isEmpty()) {
                lastSignature = newSigs.get(0).signature();
            }

            checkPendingConfirmations();

        } catch (Exception e) {
            // Must never propagate — @Scheduled thread would die permanently
            log.error("Solana watcher poll failed: {}", e.getMessage(), e);
        }
    }

    private void processNewSignature(SolanaRpcClient.SignatureInfo sig) {
        String idempotencyKey = "solana:" + sig.signature() + ":0";
        if (transferRepo.existsByIdempotencyKey(idempotencyKey)) return;

        SolanaRpcClient.TransactionDetail tx = rpc.getTransaction(sig.signature());
        if (tx == null) return;

        long usdcAmount = parseUsdcTransferAmount(tx);
        if (usdcAmount <= 0) return;

        log.info("Detected USDC deposit: sig={} amount={} minor-units", sig.signature(), usdcAmount);

        String metadata = toJson(Map.of(
                "tx_hash", sig.signature(),
                "chain", "solana",
                "slot", sig.slot()
        ));

        PostInboundCreditCommand cmd = new PostInboundCreditCommand(
                idempotencyKey,
                SystemAccounts.NORTHWIND_MAIN,
                SystemAccounts.CRYPTO_SUSPENSE,
                usdcAmount,
                "USDC",
                TransferType.CRYPTO,
                false,
                "solana",
                metadata
        );
        Transfer pendingTransfer = ledger.postInboundCredit(cmd);

        tracker.track(new ConfirmationTracker.PendingDeposit(
                sig.signature(), sig.slot(),
                pendingTransfer.getId(), usdcAmount,
                "solana", SystemAccounts.NORTHWIND_MAIN
        ));

        log.info("Pending deposit recorded: transfer={}, awaiting {} confirmations",
                pendingTransfer.getId(), confirmationThreshold);
    }

    private void checkPendingConfirmations() {
        if (tracker.getPending().isEmpty()) return;

        long currentSlot = rpc.getCurrentSlot();

        for (ConfirmationTracker.PendingDeposit deposit : tracker.getPending()) {
            long slotDepth = currentSlot - deposit.detectedAtSlot();
            if (slotDepth >= confirmationThreshold) {
                try {
                    log.info("Confirming deposit: transfer={} slotDepth={}/{}",
                            deposit.ledgerTransferId(), slotDepth, confirmationThreshold);
                    ledger.confirmTransfer(deposit.ledgerTransferId());
                    offramp.execute(deposit.ledgerTransferId(), deposit.northwindAccountId(),
                            deposit.usdcAmountMinorUnits(), deposit.chain());
                    tracker.remove(deposit.signature());
                } catch (Exception e) {
                    log.error("Failed to confirm/offramp deposit {}: {}", deposit.signature(), e.getMessage(), e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private long parseUsdcTransferAmount(SolanaRpcClient.TransactionDetail tx) {
        try {
            // Navigate jsonParsed transaction structure
            Map<?, ?> raw = (Map<?, ?>) tx.raw();
            if (raw == null) return 0L;

            Map<?, ?> transaction = (Map<?, ?>) raw.get("transaction");
            if (transaction == null) return 0L;

            Map<?, ?> message = (Map<?, ?>) transaction.get("message");
            if (message == null) return 0L;

            List<?> instructions = (List<?>) message.get("instructions");
            if (instructions == null) return 0L;

            for (Object instrObj : instructions) {
                Map<?, ?> instr = (Map<?, ?>) instrObj;
                String program = (String) instr.get("program");
                if (!"spl-token".equals(program)) continue;

                Map<?, ?> parsed = (Map<?, ?>) instr.get("parsed");
                if (parsed == null) continue;

                String type = (String) parsed.get("type");
                if (!"transferChecked".equals(type) && !"transfer".equals(type)) continue;

                Map<?, ?> info = (Map<?, ?>) parsed.get("info");
                if (info == null) continue;

                // Verify USDC mint
                String mint = (String) info.get("mint");
                if (!usdcMint.equals(mint)) continue;

                Map<?, ?> tokenAmount = (Map<?, ?>) info.get("tokenAmount");
                if (tokenAmount == null) continue;

                String amount = (String) tokenAmount.get("amount");
                if (amount != null) {
                    return Long.parseLong(amount);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse USDC amount from transaction: {}", e.getMessage());
        }
        return 0L;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
