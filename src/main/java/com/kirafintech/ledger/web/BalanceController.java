package com.kirafintech.ledger.web;

import com.kirafintech.ledger.application.port.in.BalancePort;
import com.kirafintech.ledger.exception.AccountNotFoundException;
import com.kirafintech.ledger.repository.AccountRepository;
import com.kirafintech.ledger.web.dto.BalanceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{id}/balance")
public class BalanceController {

    private static final Logger log = LoggerFactory.getLogger(BalanceController.class);

    private final BalancePort balancePort;
    private final AccountRepository accountRepo;

    public BalanceController(BalancePort balancePort, AccountRepository accountRepo) {
        this.balancePort = balancePort;
        this.accountRepo = accountRepo;
    }

    @GetMapping
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id) {
        accountRepo.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        String currency = "USD";
        long available = balancePort.getAvailableBalance(id, currency);
        long pending = balancePort.getPendingBalance(id, currency);
        log.info("getBalance account={} available={} pending={}", id, available, pending);
        return ResponseEntity.ok(new BalanceResponse(available, pending, currency));
    }
}
