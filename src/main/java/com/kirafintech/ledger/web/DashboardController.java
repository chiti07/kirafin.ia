package com.kirafintech.ledger.web;

import com.kirafintech.ledger.domain.Transfer;
import com.kirafintech.ledger.domain.enums.EntryDirection;
import com.kirafintech.ledger.domain.enums.PayoutJobStatus;
import com.kirafintech.ledger.domain.enums.TransferStatus;
import com.kirafintech.ledger.repository.AccountRepository;
import com.kirafintech.ledger.repository.EntryRepository;
import com.kirafintech.ledger.repository.PayoutJobRepository;
import com.kirafintech.ledger.repository.TransferRepository;
import com.kirafintech.ledger.service.BalanceService;
import com.kirafintech.ledger.service.ReconciliationService;
import com.kirafintech.ledger.service.SystemAccounts;
import com.kirafintech.ledger.web.dto.DashboardResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final AccountRepository accountRepo;
    private final BalanceService balanceService;
    private final TransferRepository transferRepo;
    private final EntryRepository entryRepo;
    private final PayoutJobRepository payoutJobRepo;
    private final ReconciliationService reconciliationService;

    public DashboardController(AccountRepository accountRepo, BalanceService balanceService,
                               TransferRepository transferRepo, EntryRepository entryRepo,
                               PayoutJobRepository payoutJobRepo,
                               ReconciliationService reconciliationService) {
        this.accountRepo = accountRepo;
        this.balanceService = balanceService;
        this.transferRepo = transferRepo;
        this.entryRepo = entryRepo;
        this.payoutJobRepo = payoutJobRepo;
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    public DashboardResponse get() {
        log.info("dashboard_request");

        List<DashboardResponse.AccountSummary> accounts = accountRepo.findAll().stream()
                .map(a -> new DashboardResponse.AccountSummary(
                        a.getId().toString(),
                        a.getClientName(),
                        balanceService.getAvailableBalance(a.getId(), a.getCurrency()),
                        balanceService.getPendingBalance(a.getId(), a.getCurrency()),
                        balanceService.getPendingBalance(a.getId(), "USDC"),
                        a.getCurrency()
                ))
                .toList();

        List<Transfer> recent = transferRepo.findRecent(PageRequest.of(0, 20));
        List<DashboardResponse.RecentTransfer> recentTransfers = recent.stream()
                .map(t -> new DashboardResponse.RecentTransfer(
                        t.getId().toString(),
                        t.getDirection().name(),
                        t.getType().name(),
                        t.getStatus().name(),
                        t.getAmountMinorUnits(),
                        t.getCurrency(),
                        t.getCreatedAt().toString()
                ))
                .toList();

        // Total fees = credits to the Kira Platform Fees account
        long totalFees = entryRepo.getAvailableBalance(
                SystemAccounts.KIRA_FEE_ACCOUNT, "USD", EntryDirection.CREDIT);

        DashboardResponse.FeesSummary fees = new DashboardResponse.FeesSummary(totalFees);

        DashboardResponse.PayoutJobCounts jobCounts = new DashboardResponse.PayoutJobCounts(
                payoutJobRepo.countByStatus(PayoutJobStatus.PENDING),
                payoutJobRepo.countByStatus(PayoutJobStatus.PROCESSING),
                payoutJobRepo.countByStatus(PayoutJobStatus.COMPLETED),
                payoutJobRepo.countByStatus(PayoutJobStatus.FAILED)
        );

        ReconciliationService.ReconciliationReport recon = reconciliationService.run();
        DashboardResponse.ReconciliationSummary reconSummary = new DashboardResponse.ReconciliationSummary(
                recon.settledWithNoEntryCount(),
                recon.entryNeverConfirmedCount()
        );

        return new DashboardResponse(accounts, recentTransfers, fees, jobCounts, reconSummary);
    }
}
