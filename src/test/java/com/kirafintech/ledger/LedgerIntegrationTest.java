package com.kirafintech.ledger;

import com.kirafintech.ledger.application.port.in.BalancePort;
import com.kirafintech.ledger.application.port.in.LedgerPort;
import com.kirafintech.ledger.application.port.in.LedgerPort.FeeCommand;
import com.kirafintech.ledger.application.port.in.LedgerPort.PostInboundCreditCommand;
import com.kirafintech.ledger.application.port.in.LedgerPort.PostOutboundDebitCommand;
import com.kirafintech.ledger.domain.Account;
import com.kirafintech.ledger.domain.enums.EntryDirection;
import com.kirafintech.ledger.domain.enums.TransferType;
import com.kirafintech.ledger.exception.InsufficientFundsException;
import com.kirafintech.ledger.repository.AccountRepository;
import com.kirafintech.ledger.repository.EntryRepository;
import com.kirafintech.ledger.repository.TransferRepository;
import com.kirafintech.ledger.service.SystemAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests against the local docker-compose postgres (localhost:5432).
 * Start postgres with: docker compose up -d
 * Uses the "test" profile which disables scheduled workers to avoid interference.
 */
@SpringBootTest
@ActiveProfiles("test")
class LedgerIntegrationTest {

    @Autowired LedgerPort ledger;
    @Autowired BalancePort balanceService;
    @Autowired AccountRepository accountRepo;
    @Autowired EntryRepository entryRepo;
    @Autowired TransferRepository transferRepo;

    private UUID testAccountId;

    @BeforeEach
    void setUp() {
        Account account = new Account();
        account.setClientName("Test Account " + UUID.randomUUID());
        account.setCurrency("USD");
        accountRepo.save(account);
        testAccountId = account.getId();

        // Seed $100 available balance
        ledger.postInboundCredit(new PostInboundCreditCommand(
                "seed:" + testAccountId,
                testAccountId, SystemAccounts.KIRA_LIQUIDITY_POOL,
                10_000L, "USD", TransferType.FIAT, true, null, null));
    }

    // --- Test 1: No negative balance under concurrency ---

    @Test
    void concurrentDebits_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            exec.submit(() -> {
                try {
                    ledger.postOutboundDebit(new PostOutboundDebitCommand(
                            "concurrent-debit-" + idx + "-" + testAccountId,
                            testAccountId, SystemAccounts.KIRA_LIQUIDITY_POOL,
                            6_000L, "USD", TransferType.FIAT,
                            "routing=x;account=y", "fiat_ach", "simple_rail",
                            0L, List.of(), null));
                    successes.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        exec.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(9);
        assertThat(balanceService.getAvailableBalance(testAccountId, "USD")).isEqualTo(4_000L);
    }

    // --- Test 2: Idempotency ---

    @Test
    void duplicateIdempotencyKey_returnsOriginalTransfer() {
        String key = "idem-test-" + UUID.randomUUID();
        PostInboundCreditCommand cmd = new PostInboundCreditCommand(
                key, testAccountId, SystemAccounts.KIRA_LIQUIDITY_POOL,
                1_000L, "USD", TransferType.FIAT, true, null, null);

        var first = ledger.postInboundCredit(cmd);
        var second = ledger.postInboundCredit(cmd);

        assertThat(first.getId()).isEqualTo(second.getId());

        long entryCount = entryRepo.findAll().stream()
                .filter(e -> e.getTransferId().equals(first.getId())).count();
        assertThat(entryCount).isEqualTo(2);
    }

    // --- Test 3: Double-entry invariant ---

    @Test
    void doubleEntryInvariant_alwaysZeroNet() {
        ledger.postInboundCredit(new PostInboundCreditCommand(
                "inv-test-" + UUID.randomUUID(), testAccountId, SystemAccounts.KIRA_LIQUIDITY_POOL,
                5_000L, "USD", TransferType.FIAT, true, null, null));

        long net = entryRepo.getGlobalDoubleEntryNet(EntryDirection.CREDIT);
        assertThat(net).isEqualTo(0L);
    }

    // --- Test 4: Unconfirmed deposit not spendable ---

    @Test
    void unconfirmedDeposit_notSpendable() {
        String key = "unconfirmed-" + UUID.randomUUID();
        ledger.postInboundCredit(new PostInboundCreditCommand(
                key, testAccountId, SystemAccounts.CRYPTO_SUSPENSE,
                50_000L, "USD", TransferType.CRYPTO, false, "solana", null));

        long available = balanceService.getAvailableBalance(testAccountId, "USD");
        long pending = balanceService.getPendingBalance(testAccountId, "USD");

        assertThat(available).isEqualTo(10_000L);
        assertThat(pending).isGreaterThan(available);

        assertThatThrownBy(() -> ledger.postOutboundDebit(new PostOutboundDebitCommand(
                "debit-unconfirmed-" + UUID.randomUUID(),
                testAccountId, SystemAccounts.KIRA_LIQUIDITY_POOL,
                50_000L, "USD", TransferType.FIAT,
                "routing=x;account=y", "fiat_ach", "simple_rail",
                0L, List.of(), null)))
                .isInstanceOf(InsufficientFundsException.class);
    }

    // --- Test 5: Confirm upgrade makes funds spendable ---

    @Test
    void confirmTransfer_makesFundsAvailable() {
        String key = "to-confirm-" + UUID.randomUUID();
        var t = ledger.postInboundCredit(new PostInboundCreditCommand(
                key, testAccountId, SystemAccounts.CRYPTO_SUSPENSE,
                5_000L, "USD", TransferType.CRYPTO, false, "solana", null));

        assertThat(balanceService.getAvailableBalance(testAccountId, "USD")).isEqualTo(10_000L);

        ledger.confirmTransfer(t.getId());

        assertThat(balanceService.getAvailableBalance(testAccountId, "USD")).isEqualTo(15_000L);
    }
}
