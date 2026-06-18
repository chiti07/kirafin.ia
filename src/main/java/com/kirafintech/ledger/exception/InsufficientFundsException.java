package com.kirafintech.ledger.exception;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, long available, long required) {
        super("Account %s has %d available but %d required".formatted(accountId, available, required));
    }
}
