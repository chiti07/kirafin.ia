package com.kirafintech.ledger.web;

import com.kirafintech.ledger.domain.Account;
import com.kirafintech.ledger.exception.AccountNotFoundException;
import com.kirafintech.ledger.repository.AccountRepository;
import com.kirafintech.ledger.web.dto.AccountResponse;
import com.kirafintech.ledger.web.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountRepository accountRepo;

    public AccountController(AccountRepository accountRepo) {
        this.accountRepo = accountRepo;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest req) {
        log.info("createAccount clientName={} omnibus={} parent={}", req.clientName(), req.omnibus(), req.parentAccountId());
        Account account = new Account();
        account.setClientName(req.clientName());
        account.setOmnibus(req.omnibus());
        if (req.parentAccountId() != null) {
            account.setParent(accountRepo.getReferenceById(UUID.fromString(req.parentAccountId())));
        }
        accountRepo.save(account);
        log.info("createAccount created id={} clientName={}", account.getId(), req.clientName());
        return ResponseEntity.status(201).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        return accountRepo.findById(id)
                .map(a -> ResponseEntity.ok(AccountResponse.from(a)))
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
