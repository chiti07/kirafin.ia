package com.kirafintech.ledger.bdd;

import com.kirafintech.ledger.application.port.in.BalancePort;
import com.kirafintech.ledger.application.port.in.LedgerPort;
import com.kirafintech.ledger.application.port.in.LedgerPort.PostInboundCreditCommand;
import com.kirafintech.ledger.domain.Account;
import com.kirafintech.ledger.domain.enums.TransferType;
import com.kirafintech.ledger.repository.AccountRepository;
import com.kirafintech.ledger.repository.PayoutJobRepository;
import com.kirafintech.ledger.repository.TransferRepository;
import com.kirafintech.ledger.service.OfframpService;
import com.kirafintech.ledger.service.SystemAccounts;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class NorthwindFlowSteps {

    @Autowired private LedgerPort ledger;
    @Autowired private BalancePort balanceService;
    @Autowired private OfframpService offrampService;
    @Autowired private AccountRepository accountRepo;
    @Autowired private TransferRepository transferRepo;
    @Autowired private PayoutJobRepository payoutJobRepo;

    private UUID accountId;
    private long balanceBefore;
    private String lastIdempotencyKey;

    @Before
    public void setup() {
        Account account = new Account();
        account.setClientName("BDD-Northwind-" + UUID.randomUUID());
        account.setCurrency("USD");
        accountRepo.save(account);
        accountId = account.getId();
    }

    @Given("a Northwind sub-account exists")
    public void northwindAccountExists() {
        assertThat(accountId).isNotNull();
    }

    @Given("Northwind has a zero balance")
    public void northwindHasZeroBalance() {
        balanceBefore = balanceService.getAvailableBalance(accountId, "USD");
        assertThat(balanceBefore).isZero();
    }

    @When("a confirmed USDC deposit of {long} minor units arrives")
    public void confirmedUsdcDepositArrives(long minorUnits) {
        balanceBefore = balanceService.getAvailableBalance(accountId, "USD");
        String key = "bdd-confirmed-" + UUID.randomUUID();
        lastIdempotencyKey = key;

        PostInboundCreditCommand cmd = new PostInboundCreditCommand(
                key, accountId, SystemAccounts.CRYPTO_SUSPENSE,
                minorUnits, "USDC", TransferType.CRYPTO, true, "solana", null);
        var transfer = ledger.postInboundCredit(cmd);

        offrampService.execute(transfer.getId(), accountId, minorUnits, "solana");
    }

    @When("an unconfirmed USDC deposit of {long} minor units arrives")
    public void unconfirmedUsdcDepositArrives(long minorUnits) {
        balanceBefore = balanceService.getAvailableBalance(accountId, "USD");
        String key = "bdd-unconfirmed-" + UUID.randomUUID();
        PostInboundCreditCommand cmd = new PostInboundCreditCommand(
                key, accountId, SystemAccounts.CRYPTO_SUSPENSE,
                minorUnits, "USDC", TransferType.CRYPTO, false, "solana", null);
        ledger.postInboundCredit(cmd);
    }

    @Given("a confirmed USDC deposit of {long} minor units with key {string}")
    public void confirmedDepositWithKey(long minorUnits, String key) {
        lastIdempotencyKey = key;
        PostInboundCreditCommand cmd = new PostInboundCreditCommand(
                key, accountId, SystemAccounts.CRYPTO_SUSPENSE,
                minorUnits, "USDC", TransferType.CRYPTO, true, "solana", null);
        ledger.postInboundCredit(cmd);
    }

    @When("the same deposit with key {string} is submitted again")
    public void sameDepositSubmittedAgain(String key) {
        PostInboundCreditCommand cmd = new PostInboundCreditCommand(
                key, accountId, SystemAccounts.CRYPTO_SUSPENSE,
                1_000_000_000L, "USDC", TransferType.CRYPTO, true, "solana", null);
        ledger.postInboundCredit(cmd);
    }

    @Then("the available balance increases by {long} cents")
    public void balanceIncreasesBy(long expectedIncrease) {
        long balanceAfter = balanceService.getAvailableBalance(accountId, "USD");
        assertThat(balanceAfter - balanceBefore).isEqualTo(expectedIncrease);
    }

    @And("itemized fees of {long} cents are posted to the platform fee account")
    public void feesPostedToPlatformAccount(long expectedFees) {
        long feeBalance = balanceService.getAvailableBalance(SystemAccounts.KIRA_FEE_ACCOUNT, "USD");
        assertThat(feeBalance).isGreaterThanOrEqualTo(expectedFees);
    }

    @Then("only one transfer record exists for key {string}")
    public void onlyOneTransferForKey(String key) {
        assertThat(transferRepo.findByIdempotencyKey(key)).isPresent();
        long count = transferRepo.findAll().stream()
                .filter(t -> key.equals(t.getIdempotencyKey())).count();
        assertThat(count).isEqualTo(1);
    }

    @And("the balance reflects a single credit only")
    public void balanceReflectsSingleCredit() {
        // With key "idem-bdd-001" and 1,000,000,000 minor units (= 100,000 gross cents)
        // net = 99,900 cents. Balance should not be doubled.
        long balance = balanceService.getAvailableBalance(accountId, "USD");
        assertThat(balance).isLessThanOrEqualTo(99_900L);
    }

    @Then("the available USD balance is zero")
    public void availableUsdBalanceIsZero() {
        long available = balanceService.getAvailableBalance(accountId, "USD");
        assertThat(available).isZero();
    }

    @And("the pending USDC balance is greater than zero")
    public void pendingUsdcBalanceGreaterThanZero() {
        long pending = balanceService.getPendingBalance(accountId, "USDC");
        assertThat(pending).isGreaterThan(0L);
    }

    @When("a confirmed USDC deposit of {long} minor units arrives for the Northwind main account")
    public void confirmedUsdcDepositForNorthwind(long minorUnits) {
        String key = "bdd-northwind-route-" + java.util.UUID.randomUUID();
        PostInboundCreditCommand cmd = new PostInboundCreditCommand(
                key, SystemAccounts.NORTHWIND_MAIN, SystemAccounts.CRYPTO_SUSPENSE,
                minorUnits, "USDC", TransferType.CRYPTO, true, "solana", null);
        var transfer = ledger.postInboundCredit(cmd);
        offrampService.execute(transfer.getId(), SystemAccounts.NORTHWIND_MAIN, minorUnits, "solana");
    }

    @Then("a payout job is created for the outbound route")
    public void payoutJobCreated() {
        long jobCount = payoutJobRepo.count();
        assertThat(jobCount).isGreaterThanOrEqualTo(1);
    }

    @And("the payout job status is {string} or {string}")
    public void payoutJobStatusIs(String status1, String status2) {
        boolean hasMatchingJob = payoutJobRepo.findAll().stream()
                .anyMatch(j -> status1.equalsIgnoreCase(j.getStatus().name()) ||
                               status2.equalsIgnoreCase(j.getStatus().name()));
        assertThat(hasMatchingJob).isTrue();
    }
}
