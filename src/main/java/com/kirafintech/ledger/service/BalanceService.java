package com.kirafintech.ledger.service;

import com.kirafintech.ledger.application.port.in.BalancePort;
import com.kirafintech.ledger.domain.enums.EntryDirection;
import com.kirafintech.ledger.exception.AccountNotFoundException;
import com.kirafintech.ledger.exception.InsufficientFundsException;
import com.kirafintech.ledger.repository.AccountRepository;
import com.kirafintech.ledger.repository.EntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BalanceService implements BalancePort {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final EntryRepository entryRepo;
    private final AccountRepository accountRepo;

    public BalanceService(EntryRepository entryRepo, AccountRepository accountRepo) {
        this.entryRepo = entryRepo;
        this.accountRepo = accountRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public long getAvailableBalance(UUID accountId, String currency) {
        long balance = entryRepo.getAvailableBalance(accountId, currency, EntryDirection.CREDIT);
        log.debug("getAvailableBalance account={} currency={} balance={}", accountId, currency, balance);
        return balance;
    }

    @Override
    @Transactional(readOnly = true)
    public long getPendingBalance(UUID accountId, String currency) {
        long balance = entryRepo.getPendingBalance(accountId, currency, EntryDirection.CREDIT);
        log.debug("getPendingBalance account={} currency={} balance={}", accountId, currency, balance);
        return balance;
    }

    /**
     * Acquires SELECT FOR UPDATE on the account row, re-derives available balance,
     * asserts >= requiredAmount. MUST be called within an existing @Transactional context
     * (Propagation.MANDATORY) — the lock releases at transaction commit, not method return.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndVerifyBalance(UUID accountId, String currency, long requiredAmount) {
        accountRepo.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        long available = entryRepo.getAvailableBalance(accountId, currency, EntryDirection.CREDIT);
        log.debug("lockAndVerifyBalance account={} available={} required={}", accountId, available, requiredAmount);
        if (available < requiredAmount) {
            log.warn("Insufficient funds account={} available={} required={}", accountId, available, requiredAmount);
            throw new InsufficientFundsException(accountId, available, requiredAmount);
        }
    }
}
